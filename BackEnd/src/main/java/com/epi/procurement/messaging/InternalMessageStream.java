package com.epi.procurement.messaging;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.epi.procurement.identity.CurrentUser;
import com.epi.procurement.identity.UserDirectory;
import com.epi.procurement.platform.web.ApiProblemException;

import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpSession;

/**
 * Live delivery for internal messaging over Server-Sent Events (docs/architecture/internal-messaging.md).
 *
 * <ul>
 * <li><b>Identifiers only.</b> An event says "conversation 12 has message 340" or "your read marker moved". The
 * browser then fetches through the REST endpoints, which re-check access. A stream never carries message text, names
 * or request details.</li>
 * <li><b>After commit.</b> Events leave only once the message is stored; delivery runs on a small executor so the
 * sender's request is not held, and each recipient is re-authorised just before sending.</li>
 * <li><b>Lifecycle.</b> At most {@value #PER_USER} streams per user (the oldest closes) and {@value #GLOBAL} in all.
 * Every {@value #HEARTBEAT_SECONDS} s each stream is checked: an invalidated session (sign-out) or an inactive
 * account closes it. Streams also end after 30 minutes; the browser reconnects and resynchronises from the
 * database, so nothing depends on an event arriving.</li>
 * <li><b>Single node.</b> Connections live in this JVM. With several backend nodes a message stored on one node is not
 * pushed to streams on another until their next resynchronisation; see the document for the options.</li>
 * </ul>
 */
@Component
class InternalMessageStream {

	static final int PER_USER = 5;

	static final int GLOBAL = 500;

	static final int HEARTBEAT_SECONDS = 25;

	private static final Duration LIFETIME = Duration.ofMinutes(30);

	private static final Logger log = LoggerFactory.getLogger(InternalMessageStream.class);

	private final InternalMessagingService messaging;

	private final UserDirectory users;

	private final Map<Long, List<Connection>> byUser = new ConcurrentHashMap<>();

	private final AtomicInteger open = new AtomicInteger();

	// Bounded: if delivery falls behind, new events are dropped and browsers catch up on their next resync.
	private final ThreadPoolExecutor delivery = new ThreadPoolExecutor(1, 2, 60, TimeUnit.SECONDS,
			new ArrayBlockingQueue<>(1_000), r -> {
				Thread t = new Thread(r, "internal-messaging-delivery");
				t.setDaemon(true);
				return t;
			}, new ThreadPoolExecutor.DiscardPolicy());

	InternalMessageStream(InternalMessagingService messaging, UserDirectory users) {
		this.messaging = messaging;
		this.users = users;
	}

	/** Open live streams, for monitoring (audit A-2): each one holds a server thread-free connection and a slot. */
	@org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
	void registerMetrics(org.springframework.boot.context.event.ApplicationReadyEvent ready) {
		ready.getApplicationContext()
			.getBeanProvider(io.micrometer.core.instrument.MeterRegistry.class)
			.ifAvailable(meters -> io.micrometer.core.instrument.Gauge
				.builder("procurement.messaging.streams.open", open, AtomicInteger::get)
				.description("Open internal-messaging event streams")
				.register(meters));
	}

	private record Connection(String id, long userId, SseEmitter emitter, HttpSession session) {
	}

	SseEmitter open(CurrentUser user, HttpSession session) {
		if (open.get() >= GLOBAL) {
			throw new ApiProblemException(HttpStatus.SERVICE_UNAVAILABLE, "too-many-streams",
					"Live updates are busy; the page keeps working and refreshes on its own.");
		}
		SseEmitter emitter = new SseEmitter(LIFETIME.toMillis());
		Connection connection = new Connection(UUID.randomUUID().toString(), user.id(), emitter, session);
		List<Connection> mine = byUser.computeIfAbsent(user.id(), k -> new CopyOnWriteArrayList<>());
		mine.add(connection);
		open.incrementAndGet();
		while (mine.size() > PER_USER) {
			close(mine.get(0));
		}
		emitter.onCompletion(() -> forget(connection));
		emitter.onTimeout(() -> close(connection));
		emitter.onError(e -> forget(connection));
		// First event: the browser treats it as "connected" and resynchronises from the database.
		send(connection, SseEmitter.event().name("ready").id("ready-" + connection.id()).data("{}"));
		return emitter;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	void onPosted(InternalMessagePosted event) {
		delivery.execute(() -> {
			// The sender's other tabs need the message too; their unread count does not change.
			java.util.Set<Long> targets = new java.util.LinkedHashSet<>(event.recipientUserIds());
			targets.add(event.senderUserId());
			for (long userId : targets) {
				List<Connection> connections = byUser.get(userId);
				if (connections == null || connections.isEmpty()) {
					continue;
				}
				if (!messaging.deliverable(userId, event.conversationId())) {
					continue;
				}
				String data = "{\"conversationId\":" + event.conversationId() + ",\"messageId\":" + event.messageId() + "}";
				for (Connection c : connections) {
					send(c, SseEmitter.event().name("message").id("m-" + event.messageId()).data(data));
				}
			}
		});
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	void onRead(InternalReadMarked event) {
		delivery.execute(() -> {
			List<Connection> connections = byUser.get(event.userId());
			if (connections == null) {
				return;
			}
			String data = "{\"conversationId\":" + event.conversationId() + "}";
			for (Connection c : connections) {
				send(c, SseEmitter.event().name("read").id("r-" + event.conversationId() + "-" + System.nanoTime()).data(data));
			}
		});
	}

	/** Closes streams whose session ended or whose account is no longer active, and keeps the others alive. */
	@Scheduled(fixedDelay = HEARTBEAT_SECONDS * 1000L, initialDelay = HEARTBEAT_SECONDS * 1000L)
	void heartbeat() {
		for (Map.Entry<Long, List<Connection>> entry : byUser.entrySet()) {
			boolean active = users.loadCurrentUser(entry.getKey()).isPresent();
			for (Connection c : entry.getValue()) {
				if (!active || !sessionAlive(c.session())) {
					close(c);
				}
				else {
					send(c, SseEmitter.event().comment("hb"));
				}
			}
		}
	}

	/** Closes every stream of a user at once (used when an account is deactivated). */
	void closeAll(long userId) {
		List<Connection> connections = byUser.get(userId);
		if (connections != null) {
			connections.forEach(this::close);
		}
	}

	int openStreams(long userId) {
		List<Connection> connections = byUser.get(userId);
		return connections == null ? 0 : connections.size();
	}

	@PreDestroy
	void shutdown() {
		byUser.values().forEach(list -> list.forEach(this::close));
		delivery.shutdownNow();
	}

	private static boolean sessionAlive(HttpSession session) {
		if (session == null) {
			return false;
		}
		try {
			session.getLastAccessedTime();
			return true;
		}
		catch (IllegalStateException invalidated) {
			return false;
		}
	}

	private void send(Connection c, SseEmitter.SseEventBuilder event) {
		try {
			synchronized (c) {
				c.emitter().send(event);
			}
		}
		catch (IOException | IllegalStateException ex) {
			log.debug("Live stream {} closed while sending: {}", c.id(), ex.getMessage());
			close(c);
		}
	}

	private void close(Connection c) {
		forget(c);
		try {
			c.emitter().complete();
		}
		catch (RuntimeException ignored) {
			// Already completed.
		}
	}

	private void forget(Connection c) {
		List<Connection> connections = byUser.get(c.userId());
		if (connections != null && connections.remove(c)) {
			open.decrementAndGet();
			if (connections.isEmpty()) {
				byUser.remove(c.userId(), connections);
			}
		}
	}

}

package com.epi.procurement.notification;

import com.epi.procurement.platform.persistence.UtcTimes;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.epi.procurement.identity.CurrentUser;
import com.epi.procurement.identity.CurrentUserProvider;
import com.epi.procurement.platform.web.ApiProblemException;

/**
 * In-app notifications of the signed-in user (S2-AC-10). A notification of another user behaves as not found.
 */
@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController {

	private final JdbcTemplate jdbc;

	private final CurrentUserProvider currentUser;

	private final Clock clock;

	NotificationController(DataSource dataSource, CurrentUserProvider currentUser, ObjectProvider<Clock> clock) {
		this.jdbc = new JdbcTemplate(dataSource);
		this.currentUser = currentUser;
		this.clock = clock.getIfAvailable(Clock::systemUTC);
	}

	@GetMapping
	NotificationPage list(@RequestParam(defaultValue = "false") boolean unread, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		CurrentUser user = currentUser.require();
		if (page < 0) {
			throw ApiProblemException.invalidParameter("page");
		}
		if (size < 1 || size > 100) {
			throw ApiProblemException.invalidParameter("size");
		}
		String filter = unread ? " AND read_at IS NULL" : "";
		Long total = jdbc.queryForObject("SELECT COUNT(*) FROM in_app_notification WHERE user_id = ?" + filter, Long.class,
				user.id());
		Long unreadCount = jdbc.queryForObject(
				"SELECT COUNT(*) FROM in_app_notification WHERE user_id = ? AND read_at IS NULL", Long.class, user.id());
		List<Item> items = jdbc.query("SELECT id, kind, request_id, title, body, link, created_at, read_at "
				+ "FROM in_app_notification WHERE user_id = ?" + filter
				+ " ORDER BY created_at DESC, id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
				(rs, i) -> new Item(rs.getLong("id"), rs.getString("kind"), rs.getObject("request_id", Long.class),
						rs.getString("title"), rs.getString("body"), rs.getString("link"),
						UtcTimes.instant(rs, "created_at"),
						UtcTimes.instant(rs, "read_at")),
				user.id(), page * size, size);
		long totalItems = total == null ? 0 : total;
		return new NotificationPage(items, page, size, totalItems, (int) ((totalItems + size - 1) / size),
				unreadCount == null ? 0 : unreadCount);
	}

	@GetMapping("/unread-count")
	UnreadCount unreadCount() {
		Long count = jdbc.queryForObject(
				"SELECT COUNT(*) FROM in_app_notification WHERE user_id = ? AND read_at IS NULL", Long.class,
				currentUser.require().id());
		return new UnreadCount(count == null ? 0 : count);
	}

	@PostMapping("/{id}/read")
	ResponseEntity<Void> markRead(@PathVariable long id) {
		int updated = jdbc.update("UPDATE in_app_notification SET read_at = COALESCE(read_at, ?) WHERE id = ? AND user_id = ?",
				UtcTimes.db(clock.instant()), id, currentUser.require().id());
		if (updated == 0) {
			throw ApiProblemException.notFound();
		}
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/read-all")
	ResponseEntity<Void> markAllRead() {
		jdbc.update("UPDATE in_app_notification SET read_at = ? WHERE user_id = ? AND read_at IS NULL",
				UtcTimes.db(clock.instant()), currentUser.require().id());
		return ResponseEntity.noContent().build();
	}

	record Item(long id, String kind, Long requestId, String title, String body, String link, Instant createdAt,
			Instant readAt) {
	}

	record NotificationPage(List<Item> items, int page, int size, long totalItems, int totalPages, long unreadCount) {
	}

	record UnreadCount(long unread) {
	}

}

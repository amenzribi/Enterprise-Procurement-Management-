package com.epi.procurement.approval;

import static com.epi.procurement.platform.persistence.UtcTimes.db;
import static com.epi.procurement.platform.persistence.UtcTimes.instant;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

/**
 * Plain-SQL access to the approval tables. Approval data never names the request tables (S1CR-08): request data
 * comes from the request module's API.
 */
@Component
class ApprovalStore {

	private static final String RULE_COLUMNS = """
			id, group_id, company_id, rule_key, version_no, name, category_id, purchase_type, min_amount, max_amount,
			status, created_at, activated_at""";

	private static final String TASK_COLUMNS = """
			id, round_id, request_id, company_id, stage_no, stage_name, approver_type, policy, assignee_user_id,
			original_user_id, routing_note, status, sla_minutes, escalation_role, due_at, escalation_at, paused_at,
			question_open, decided_at, decided_by, decision_comment, created_at, version""";

	private static final String ROUND_COLUMNS = """
			id, request_id, company_id, round_no, rule_id, request_amount, request_currency, company_amount,
			company_currency, rate, rate_date, status, current_stage_no, started_at, completed_at, version""";

	private final JdbcTemplate jdbc;

	ApprovalStore(DataSource dataSource) {
		this.jdbc = new JdbcTemplate(dataSource);
	}

	// Rules -----------------------------------------------------------------------------------------------------

	List<Rule> activeRulesOfCompany(long companyId) {
		return withStages(jdbc.query("SELECT " + RULE_COLUMNS + " FROM approval_rule WHERE company_id = ? AND status = 'ACTIVE'",
				this::ruleRow, companyId));
	}

	Optional<Rule> rule(long id) {
		return withStages(jdbc.query("SELECT " + RULE_COLUMNS + " FROM approval_rule WHERE id = ?", this::ruleRow, id))
			.stream()
			.findFirst();
	}

	List<Rule> ruleVersions(long ruleKey) {
		return withStages(jdbc.query("SELECT " + RULE_COLUMNS + " FROM approval_rule WHERE rule_key = ? ORDER BY version_no",
				this::ruleRow, ruleKey));
	}

	/**
	 * The latest version of every rule of the group (optionally of one company).
	 */
	List<Rule> latestRules(long groupId, Long companyId) {
		String sql = "SELECT " + RULE_COLUMNS + " FROM approval_rule r WHERE group_id = ?"
				+ (companyId != null ? " AND company_id = ?" : "")
				+ " AND version_no = (SELECT MAX(version_no) FROM approval_rule v WHERE v.rule_key = r.rule_key)"
				+ " ORDER BY company_id, min_amount, id";
		return withStages(companyId != null ? jdbc.query(sql, this::ruleRow, groupId, companyId)
				: jdbc.query(sql, this::ruleRow, groupId));
	}

	long insertRule(long groupId, long companyId, Long ruleKey, int versionNo, String name, Long categoryId,
			String purchaseType, BigDecimal min, BigDecimal max, long createdBy, Instant now, List<Stage> stages) {
		Long id = jdbc.queryForObject("SELECT NEXT VALUE FOR seq_approval_rule", Long.class);
		jdbc.update("""
				INSERT INTO approval_rule (id, group_id, company_id, rule_key, version_no, name, category_id, purchase_type,
				                           min_amount, max_amount, status, created_by, created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?)""", id, groupId, companyId,
				ruleKey == null ? id : ruleKey, versionNo, name, categoryId, purchaseType, min, max, createdBy, db(now));
		insertStages(id, stages);
		return id;
	}

	void updateDraftRule(long id, String name, Long categoryId, String purchaseType, BigDecimal min, BigDecimal max,
			List<Stage> stages) {
		jdbc.update("""
				UPDATE approval_rule SET name = ?, category_id = ?, purchase_type = ?, min_amount = ?, max_amount = ?
				WHERE id = ? AND status = 'DRAFT'""", name, categoryId, purchaseType, min, max, id);
		jdbc.update("DELETE FROM approval_rule_stage WHERE rule_id = ?", id);
		insertStages(id, stages);
	}

	void activateRule(long id, long ruleKey, Instant now) {
		jdbc.update("UPDATE approval_rule SET status = 'RETIRED' WHERE rule_key = ? AND status = 'ACTIVE' AND id <> ?",
				ruleKey, id);
		jdbc.update("UPDATE approval_rule SET status = 'ACTIVE', activated_at = ? WHERE id = ?", db(now), id);
	}

	void retireRule(long id) {
		jdbc.update("UPDATE approval_rule SET status = 'RETIRED' WHERE id = ? AND status = 'ACTIVE'", id);
	}

	boolean categoryExists(long groupId, long categoryId) {
		Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM request_category WHERE group_id = ? AND id = ? AND is_active = 1",
				Integer.class, groupId, categoryId);
		return count != null && count > 0;
	}

	boolean ruleUsed(long ruleId) {
		Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM approval_round WHERE rule_id = ?", Integer.class, ruleId);
		return count != null && count > 0;
	}

	private void insertStages(long ruleId, List<Stage> stages) {
		for (Stage stage : stages) {
			jdbc.update("""
					INSERT INTO approval_rule_stage (rule_id, stage_no, name, approver_type, approver_user_id, policy, sla_hours,
					                                 escalation_role, min_amount)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""", ruleId, stage.stageNo(), stage.name(), stage.approverType(),
					stage.approverUserId(), stage.policy(), stage.slaHours(), stage.escalationRole(), stage.minAmount());
		}
	}

	private List<Rule> withStages(List<Rule> rules) {
		if (rules.isEmpty()) {
			return rules;
		}
		Map<Long, List<Stage>> stages = new LinkedHashMap<>();
		rules.forEach(r -> stages.put(r.id(), new ArrayList<>()));
		String ids = String.join(",", rules.stream().map(r -> Long.toString(r.id())).toList());
		jdbc.query("SELECT rule_id, stage_no, name, approver_type, approver_user_id, policy, sla_hours, escalation_role, "
				+ "min_amount FROM approval_rule_stage WHERE rule_id IN (" + ids + ") ORDER BY rule_id, stage_no", rs -> {
					stages.get(rs.getLong("rule_id"))
						.add(new Stage(rs.getInt("stage_no"), rs.getString("name"), rs.getString("approver_type"),
								rs.getObject("approver_user_id", Long.class), rs.getString("policy"),
								rs.getInt("sla_hours"), rs.getString("escalation_role"), rs.getBigDecimal("min_amount")));
				});
		return rules.stream().map(r -> r.withStages(List.copyOf(stages.get(r.id())))).toList();
	}

	private Rule ruleRow(ResultSet rs, int i) throws SQLException {
		return new Rule(rs.getLong("id"), rs.getLong("group_id"), rs.getLong("company_id"), rs.getLong("rule_key"),
				rs.getInt("version_no"), rs.getString("name"), rs.getObject("category_id", Long.class),
				rs.getString("purchase_type"), rs.getBigDecimal("min_amount"), rs.getBigDecimal("max_amount"),
				rs.getString("status"), instant(rs, "created_at"), instant(rs, "activated_at"), List.of());
	}

	// Rounds ----------------------------------------------------------------------------------------------------

	long insertRound(long requestId, long companyId, int roundNo, long ruleId, BigDecimal requestAmount,
			String requestCurrency, BigDecimal companyAmount, String companyCurrency, BigDecimal rate, LocalDate rateDate,
			int firstStage, Instant now) {
		Long id = jdbc.queryForObject("SELECT NEXT VALUE FOR seq_approval_round", Long.class);
		jdbc.update("""
				INSERT INTO approval_round (id, request_id, company_id, round_no, rule_id, request_amount, request_currency,
				                            company_amount, company_currency, rate, rate_date, status, current_stage_no,
				                            started_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)""", id, requestId, companyId, roundNo, ruleId,
				requestAmount, requestCurrency, companyAmount, companyCurrency, rate, rateDate, firstStage, db(now));
		return id;
	}

	Round lockRound(long id) {
		return jdbc.queryForObject("SELECT " + ROUND_COLUMNS + " FROM approval_round WITH (UPDLOCK, ROWLOCK) WHERE id = ?",
				this::roundRow, id);
	}

	List<Round> roundsOfRequest(long requestId) {
		return jdbc.query("SELECT " + ROUND_COLUMNS + " FROM approval_round WHERE request_id = ? ORDER BY round_no", this::roundRow,
				requestId);
	}

	Optional<Round> activeRound(long requestId) {
		return jdbc.query("SELECT " + ROUND_COLUMNS + " FROM approval_round WHERE request_id = ? AND status = 'ACTIVE'",
				this::roundRow, requestId).stream().findFirst();
	}

	void updateRound(long id, String status, int currentStage, Instant completedAt) {
		jdbc.update("""
				UPDATE approval_round SET status = ?, current_stage_no = ?, completed_at = ?, version = version + 1
				WHERE id = ?""", status, currentStage, db(completedAt), id);
	}

	private Round roundRow(ResultSet rs, int i) throws SQLException {
		return new Round(rs.getLong("id"), rs.getLong("request_id"), rs.getLong("company_id"), rs.getInt("round_no"),
				rs.getLong("rule_id"), rs.getBigDecimal("request_amount"), rs.getString("request_currency"),
				rs.getBigDecimal("company_amount"), rs.getString("company_currency"), rs.getBigDecimal("rate"),
				rs.getObject("rate_date", LocalDate.class), rs.getString("status"), rs.getInt("current_stage_no"),
				instant(rs, "started_at"), instant(rs, "completed_at"), rs.getInt("version"));
	}

	// Tasks -----------------------------------------------------------------------------------------------------

	long insertTask(NewTask t) {
		Long id = jdbc.queryForObject("SELECT NEXT VALUE FOR seq_approval_task", Long.class);
		jdbc.update("""
				INSERT INTO approval_task (id, round_id, request_id, company_id, stage_no, stage_name, approver_type, policy,
				                           assignee_user_id, original_user_id, routing_note, status, sla_minutes,
				                           escalation_role, due_at, escalation_at, created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, ?, ?, ?)""", id, t.roundId(), t.requestId(),
				t.companyId(), t.stageNo(), t.stageName(), t.approverType(), t.policy(), t.assigneeUserId(),
				t.originalUserId(), t.routingNote(), t.slaMinutes(), t.escalationRole(), db(t.dueAt()),
				db(t.escalationAt()), db(t.createdAt()));
		return id;
	}

	Optional<Task> task(long id) {
		return jdbc.query("SELECT " + TASK_COLUMNS + " FROM approval_task WHERE id = ?", this::taskRow, id).stream().findFirst();
	}

	Task lockTask(long id) {
		return jdbc.queryForObject("SELECT " + TASK_COLUMNS + " FROM approval_task WITH (UPDLOCK, ROWLOCK) WHERE id = ?",
				this::taskRow, id);
	}

	List<Task> tasksOfRound(long roundId) {
		return jdbc.query("SELECT " + TASK_COLUMNS + " FROM approval_task WHERE round_id = ? ORDER BY stage_no, id",
				this::taskRow, roundId);
	}

	/**
	 * Locks every task of the round (step 3 of the lock order).
	 */
	List<Task> lockTasksOfRound(long roundId) {
		return jdbc.query("SELECT " + TASK_COLUMNS + " FROM approval_task WITH (UPDLOCK, ROWLOCK) WHERE round_id = ? "
				+ "ORDER BY stage_no, id", this::taskRow, roundId);
	}

	List<Task> tasksOfRequest(long requestId) {
		return jdbc.query("SELECT " + TASK_COLUMNS + " FROM approval_task WHERE request_id = ? ORDER BY id", this::taskRow,
				requestId);
	}

	List<Task> pendingTasksOfUser(long userId) {
		return jdbc.query("SELECT " + TASK_COLUMNS + " FROM approval_task WHERE assignee_user_id = ? AND status = 'PENDING' "
				+ "ORDER BY created_at, id", this::taskRow, userId);
	}

	List<Task> decidedTasksOfUser(long userId, int limit) {
		return jdbc.query("SELECT TOP (" + limit + ") " + TASK_COLUMNS + " FROM approval_task "
				+ "WHERE (assignee_user_id = ? OR decided_by = ?) AND status <> 'PENDING' ORDER BY COALESCE(decided_at, created_at) DESC, id DESC",
				this::taskRow, userId, userId);
	}

	long[] countPending(long userId, Instant now) {
		return jdbc.queryForObject("""
				SELECT COUNT(*) AS pending, SUM(CASE WHEN due_at <= ? AND question_open = 0 THEN 1 ELSE 0 END) AS overdue
				FROM approval_task WHERE assignee_user_id = ? AND status = 'PENDING'""",
				(rs, i) -> new long[] { rs.getLong("pending"), rs.getLong("overdue") }, db(now), userId);
	}

	/**
	 * The named user of the task's rule stage (NAMED_USER stages), from the round's rule version (S2CR-09).
	 */
	Long namedUserOfTask(long taskId) {
		return jdbc.query("""
				SELECT s.approver_user_id FROM approval_task t
				JOIN approval_round r ON r.id = t.round_id
				JOIN approval_rule_stage s ON s.rule_id = r.rule_id AND s.stage_no = t.stage_no
				WHERE t.id = ?""", rs -> rs.next() ? rs.getObject(1, Long.class) : null, taskId);
	}

	void decideTask(long id, String status, long decidedBy, Instant at, String comment) {
		jdbc.update("""
				UPDATE approval_task SET status = ?, decided_by = ?, decided_at = ?, decision_comment = ?, question_open = 0,
				       paused_at = NULL, version = version + 1
				WHERE id = ? AND status = 'PENDING'""", status, decidedBy, db(at), comment, id);
	}

	void cancelPendingTasks(long roundId, long exceptTaskId, String status, Instant at) {
		jdbc.update("""
				UPDATE approval_task SET status = ?, decided_at = ?, question_open = 0, paused_at = NULL, version = version + 1
				WHERE round_id = ? AND status = 'PENDING' AND id <> ?""", status, db(at), roundId, exceptTaskId);
	}

	/**
	 * Administrative cancellation / return: ends every pending task on the request and closes active rounds.
	 */
	void cancelAllPendingForRequest(long requestId, Instant at) {
		jdbc.update("""
				UPDATE approval_task SET status = 'CANCELLED', decided_at = ?, question_open = 0, paused_at = NULL,
				       version = version + 1
				WHERE request_id = ? AND status = 'PENDING'""", db(at), requestId);
		jdbc.update("""
				UPDATE approval_round SET status = 'CANCELLED', completed_at = ?, version = version + 1
				WHERE request_id = ? AND status = 'ACTIVE'""", db(at), requestId);
		jdbc.update("""
				UPDATE approval_link_token SET used_at = ?
				WHERE used_at IS NULL AND task_id IN (SELECT id FROM approval_task WHERE request_id = ?)""", db(at),
				requestId);
	}

	void openQuestion(long id, Instant at) {
		jdbc.update("UPDATE approval_task SET question_open = 1, paused_at = ?, version = version + 1 WHERE id = ?", db(at), id);
	}

	void closeQuestion(long id, Instant dueAt, Instant escalationAt) {
		jdbc.update("""
				UPDATE approval_task SET question_open = 0, paused_at = NULL, due_at = ?, escalation_at = ?, version = version + 1
				WHERE id = ?""", db(dueAt), db(escalationAt), id);
	}

	/**
	 * Pending tasks whose SLA (or escalation time) has passed and that have no reminder of that kind yet. Tasks with
	 * an open question are paused.
	 */
	List<Task> dueForReminder(String kind, Instant now) {
		String column = kind.equals("REMINDER") ? "due_at" : "escalation_at";
		return jdbc.query("SELECT " + TASK_COLUMNS + " FROM approval_task t WHERE status = 'PENDING' AND question_open = 0 AND "
				+ column + " <= ? AND NOT EXISTS (SELECT 1 FROM approval_task_reminder r WHERE r.task_id = t.id AND r.kind = ?) "
				+ "ORDER BY " + column, this::taskRow, db(now), kind);
	}

	/**
	 * Records that a reminder was sent; false when another sender already recorded it (S2DR-10).
	 */
	boolean claimReminder(long taskId, String kind, Instant now) {
		try {
			jdbc.update("INSERT INTO approval_task_reminder (task_id, kind, sent_at) VALUES (?, ?, ?)", taskId, kind, db(now));
			return true;
		}
		catch (DuplicateKeyException ex) {
			return false;
		}
	}

	private Task taskRow(ResultSet rs, int i) throws SQLException {
		return new Task(rs.getLong("id"), rs.getLong("round_id"), rs.getLong("request_id"), rs.getLong("company_id"),
				rs.getInt("stage_no"), rs.getString("stage_name"), rs.getString("approver_type"), rs.getString("policy"),
				rs.getLong("assignee_user_id"), rs.getObject("original_user_id", Long.class), rs.getString("routing_note"),
				rs.getString("status"), rs.getInt("sla_minutes"), rs.getString("escalation_role"), instant(rs, "due_at"),
				instant(rs, "escalation_at"), instant(rs, "paused_at"), rs.getBoolean("question_open"),
				instant(rs, "decided_at"), rs.getObject("decided_by", Long.class), rs.getString("decision_comment"),
				instant(rs, "created_at"), rs.getInt("version"));
	}

	// Events ----------------------------------------------------------------------------------------------------

	void insertEvent(long requestId, long roundId, Long taskId, String kind, Long actor, Long onBehalfOf, Long target,
			String text, String fromStatus, String toStatus, String channel, Instant at) {
		jdbc.update("""
				INSERT INTO approval_task_event (request_id, round_id, task_id, kind, actor_user_id, on_behalf_of_user_id,
				                                 target_user_id, text, from_status, to_status, channel, occurred_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", requestId, roundId, taskId, kind, actor, onBehalfOf, target,
				text, fromStatus, toStatus, channel, db(at));
	}

	List<Event> eventsOfRequest(long requestId) {
		return jdbc.query("""
				SELECT id, round_id, task_id, kind, actor_user_id, on_behalf_of_user_id, target_user_id, text, from_status,
				       to_status, channel, occurred_at
				FROM approval_task_event WHERE request_id = ? ORDER BY occurred_at, id""",
				(rs, i) -> new Event(rs.getLong("id"), rs.getLong("round_id"), rs.getObject("task_id", Long.class),
						rs.getString("kind"), rs.getObject("actor_user_id", Long.class),
						rs.getObject("on_behalf_of_user_id", Long.class), rs.getObject("target_user_id", Long.class),
						rs.getString("text"), rs.getString("from_status"), rs.getString("to_status"),
						rs.getString("channel"), instant(rs, "occurred_at")), requestId);
	}

	// One-tap tokens --------------------------------------------------------------------------------------------

	void insertToken(String hash, long taskId, long userId, Instant expiresAt, Instant now) {
		jdbc.update("""
				INSERT INTO approval_link_token (token_hash, task_id, user_id, expires_at, created_at)
				VALUES (?, ?, ?, ?, ?)""", hash, taskId, userId, db(expiresAt), db(now));
	}

	Optional<Token> tokenByHash(String hash) {
		return jdbc.query("SELECT id, task_id, user_id, expires_at, used_at FROM approval_link_token WHERE token_hash = ?",
				(rs, i) -> new Token(rs.getLong("id"), rs.getLong("task_id"), rs.getLong("user_id"),
						instant(rs, "expires_at"), instant(rs, "used_at")), hash).stream().findFirst();
	}

	boolean markTokenUsed(long id, Instant now) {
		return jdbc.update("UPDATE approval_link_token SET used_at = ? WHERE id = ? AND used_at IS NULL", db(now), id) == 1;
	}

	// Absences --------------------------------------------------------------------------------------------------

	long insertAbsence(long userId, long delegateUserId, Instant startsAt, Instant endsAt, String reason,
			boolean adminOverride, long createdBy, Instant now) {
		Long id = jdbc.queryForObject("SELECT NEXT VALUE FOR seq_user_absence", Long.class);
		jdbc.update("""
				INSERT INTO user_absence (id, user_id, delegate_user_id, starts_at, ends_at, reason, admin_override, created_by,
				                          created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""", id, userId, delegateUserId, db(startsAt), db(endsAt), reason,
				adminOverride, createdBy, db(now));
		return id;
	}

	List<Absence> absencesOf(long userId) {
		return jdbc.query("SELECT " + ABSENCE_COLUMNS + " FROM user_absence WHERE user_id = ? AND cancelled_at IS NULL "
				+ "ORDER BY starts_at", this::absenceRow, userId);
	}

	List<Absence> absencesOfUsers(List<Long> userIds) {
		if (userIds.isEmpty()) {
			return List.of();
		}
		String ids = String.join(",", userIds.stream().map(String::valueOf).toList());
		return jdbc.query("SELECT " + ABSENCE_COLUMNS + " FROM user_absence WHERE user_id IN (" + ids + ") "
				+ "AND cancelled_at IS NULL ORDER BY starts_at", this::absenceRow);
	}

	Optional<Absence> absence(long id) {
		return jdbc.query("SELECT " + ABSENCE_COLUMNS + " FROM user_absence WHERE id = ?", this::absenceRow, id).stream().findFirst();
	}

	Optional<Absence> activeAbsence(long userId, Instant at) {
		return jdbc.query("SELECT TOP (1) " + ABSENCE_COLUMNS + " FROM user_absence WHERE user_id = ? AND cancelled_at IS NULL "
				+ "AND starts_at <= ? AND ends_at > ? ORDER BY starts_at DESC", this::absenceRow, userId, db(at), db(at))
			.stream()
			.findFirst();
	}

	boolean overlaps(long userId, Instant startsAt, Instant endsAt) {
		Integer count = jdbc.queryForObject("""
				SELECT COUNT(*) FROM user_absence WHERE user_id = ? AND cancelled_at IS NULL AND starts_at < ? AND ends_at > ?""",
				Integer.class, userId, db(endsAt), db(startsAt));
		return count != null && count > 0;
	}

	void cancelAbsence(long id, Instant now) {
		jdbc.update("UPDATE user_absence SET cancelled_at = ? WHERE id = ? AND cancelled_at IS NULL", db(now), id);
	}

	private static final String ABSENCE_COLUMNS = """
			id, user_id, delegate_user_id, starts_at, ends_at, reason, admin_override, created_by, created_at, cancelled_at""";

	private Absence absenceRow(ResultSet rs, int i) throws SQLException {
		return new Absence(rs.getLong("id"), rs.getLong("user_id"), rs.getLong("delegate_user_id"),
				instant(rs, "starts_at"), instant(rs, "ends_at"), rs.getString("reason"), rs.getBoolean("admin_override"),
				rs.getLong("created_by"), instant(rs, "created_at"));
	}

	// Records ---------------------------------------------------------------------------------------------------

	record Rule(long id, long groupId, long companyId, long ruleKey, int versionNo, String name, Long categoryId,
			String purchaseType, BigDecimal minAmount, BigDecimal maxAmount, String status, Instant createdAt,
			Instant activatedAt, List<Stage> stages) {

		Rule withStages(List<Stage> list) {
			return new Rule(id, groupId, companyId, ruleKey, versionNo, name, categoryId, purchaseType, minAmount,
					maxAmount, status, createdAt, activatedAt, list);
		}

		/** Specificity order (S2DR-05): category + type > category > type > company default. */
		int specificity() {
			return (categoryId != null ? 2 : 0) + (purchaseType != null ? 1 : 0);
		}

		boolean matches(Long category, String type, BigDecimal amount) {
			return (categoryId == null || categoryId.equals(category))
					&& (purchaseType == null || purchaseType.equals(type)) && inBand(amount);
		}

		/** Band is [min, max). */
		boolean inBand(BigDecimal amount) {
			return amount.compareTo(minAmount) >= 0 && (maxAmount == null || amount.compareTo(maxAmount) < 0);
		}

		boolean overlaps(Rule other) {
			boolean sameScope = java.util.Objects.equals(categoryId, other.categoryId)
					&& java.util.Objects.equals(purchaseType, other.purchaseType);
			boolean thisBelowOther = maxAmount != null && maxAmount.compareTo(other.minAmount) <= 0;
			boolean otherBelowThis = other.maxAmount != null && other.maxAmount.compareTo(minAmount) <= 0;
			return sameScope && !thisBelowOther && !otherBelowThis;
		}

		List<Stage> applicableStages(BigDecimal amount) {
			return stages.stream().filter(s -> s.minAmount() == null || amount.compareTo(s.minAmount()) >= 0).toList();
		}

	}

	record Stage(int stageNo, String name, String approverType, Long approverUserId, String policy, int slaHours,
			String escalationRole, BigDecimal minAmount) {
	}

	record Round(long id, long requestId, long companyId, int roundNo, long ruleId, BigDecimal requestAmount,
			String requestCurrency, BigDecimal companyAmount, String companyCurrency, BigDecimal rate,
			LocalDate rateDate, String status, int currentStageNo, Instant startedAt, Instant completedAt, int version) {
	}

	record NewTask(long roundId, long requestId, long companyId, int stageNo, String stageName, String approverType,
			String policy, long assigneeUserId, Long originalUserId, String routingNote, int slaMinutes,
			String escalationRole, Instant dueAt, Instant escalationAt, Instant createdAt) {
	}

	record Task(long id, long roundId, long requestId, long companyId, int stageNo, String stageName,
			String approverType, String policy, long assigneeUserId, Long originalUserId, String routingNote,
			String status, int slaMinutes, String escalationRole, Instant dueAt, Instant escalationAt, Instant pausedAt,
			boolean questionOpen, Instant decidedAt, Long decidedBy, String comment, Instant createdAt, int version) {

		boolean isPending() {
			return "PENDING".equals(status);
		}

	}

	record Event(long id, long roundId, Long taskId, String kind, Long actorUserId, Long onBehalfOfUserId,
			Long targetUserId, String text, String fromStatus, String toStatus, String channel, Instant occurredAt) {
	}

	record Token(long id, long taskId, long userId, Instant expiresAt, Instant usedAt) {
	}

	record Absence(long id, long userId, long delegateUserId, Instant startsAt, Instant endsAt, String reason,
			boolean adminOverride, long createdBy, Instant createdAt) {
	}

}

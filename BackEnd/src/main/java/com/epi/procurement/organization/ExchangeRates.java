package com.epi.procurement.organization;

import com.epi.procurement.platform.persistence.UtcTimes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Exchange rates of a group (S2-AC-03, S2DR-06). A rate is "units of quote currency per 1 unit of base currency".
 * Rows are immutable: a correction is a new row, and the latest row (by rate date, then creation time) on or before
 * the requested date applies. Conversion uses the direct pair, else the inverse pair.
 */
@Service
public class ExchangeRates {

	private final JdbcTemplate jdbc;

	ExchangeRates(DataSource dataSource) {
		this.jdbc = new JdbcTemplate(dataSource);
	}

	/**
	 * Converts {@code amount} from {@code from} to {@code to} with the rate valid on {@code date}, rounded HALF_UP to
	 * the minor units of {@code to}. Same currency: rate 1, no lookup. Empty when no rate exists.
	 */
	public Optional<Conversion> convert(long groupId, BigDecimal amount, String from, String to, LocalDate date) {
		int minorUnits = minorUnits(to);
		if (from.equals(to)) {
			return Optional.of(new Conversion(amount.setScale(minorUnits, RoundingMode.HALF_UP), BigDecimal.ONE, null));
		}
		Optional<Rate> direct = latest(groupId, from, to, date);
		if (direct.isPresent()) {
			BigDecimal rate = direct.get().rate();
			return Optional.of(new Conversion(amount.multiply(rate).setScale(minorUnits, RoundingMode.HALF_UP), rate,
					direct.get().rateDate()));
		}
		return latest(groupId, to, from, date).map(inverse -> {
			// Effective rate from -> to, kept with 8 decimals as stored; the amount is computed from the stored rate.
			BigDecimal rate = BigDecimal.ONE.divide(inverse.rate(), 8, RoundingMode.HALF_UP);
			BigDecimal converted = amount.divide(inverse.rate(), minorUnits + 8, RoundingMode.HALF_UP)
				.setScale(minorUnits, RoundingMode.HALF_UP);
			return new Conversion(converted, rate, inverse.rateDate());
		});
	}

	public int minorUnits(String currency) {
		List<Integer> units = jdbc.queryForList("SELECT minor_units FROM currency WHERE code = ?", Integer.class, currency);
		return units.isEmpty() ? 2 : units.get(0);
	}

	public boolean currencyExists(String currency) {
		Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM currency WHERE code = ?", Integer.class, currency);
		return count != null && count > 0;
	}

	public List<Rate> list(long groupId) {
		return jdbc.query("""
				SELECT id, base_currency, quote_currency, rate, rate_date, source, created_at FROM exchange_rate
				WHERE group_id = ? ORDER BY rate_date DESC, created_at DESC, id DESC""", (rs, i) -> new Rate(rs.getLong("id"),
				rs.getString("base_currency"), rs.getString("quote_currency"), rs.getBigDecimal("rate"),
				rs.getObject("rate_date", LocalDate.class), rs.getString("source"),
				UtcTimes.instant(rs, "created_at")), groupId);
	}

	public long add(long groupId, String base, String quote, BigDecimal rate, LocalDate date, String source,
			long createdBy, Instant now) {
		Long id = jdbc.queryForObject("SELECT NEXT VALUE FOR seq_exchange_rate", Long.class);
		jdbc.update("""
				INSERT INTO exchange_rate (id, group_id, base_currency, quote_currency, rate, rate_date, source, created_by,
				                           created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""", id, groupId, base, quote, rate, date, source, createdBy,
				UtcTimes.db(now));
		return id;
	}

	private Optional<Rate> latest(long groupId, String base, String quote, LocalDate date) {
		return jdbc.query("""
				SELECT TOP (1) id, base_currency, quote_currency, rate, rate_date, source, created_at FROM exchange_rate
				WHERE group_id = ? AND base_currency = ? AND quote_currency = ? AND rate_date <= ?
				ORDER BY rate_date DESC, created_at DESC, id DESC""", (rs, i) -> new Rate(rs.getLong("id"),
				rs.getString("base_currency"), rs.getString("quote_currency"), rs.getBigDecimal("rate"),
				rs.getObject("rate_date", LocalDate.class), rs.getString("source"),
				UtcTimes.instant(rs, "created_at")), groupId, base, quote, date).stream().findFirst();
	}

	/**
	 * A converted amount; {@code rateDate} is null when no conversion was needed.
	 */
	public record Conversion(BigDecimal amount, BigDecimal rate, LocalDate rateDate) {
	}

	public record Rate(long id, String baseCurrency, String quoteCurrency, BigDecimal rate, LocalDate rateDate,
			String source, Instant createdAt) {
	}

}

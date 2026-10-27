package com.epi.procurement.request;

import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Reference data used by requests: currencies (with ISO 4217 minor units), units of measure and the group's
 * purchase categories.
 */
@Component
class RequestReferenceData {

	private final JdbcTemplate jdbc;

	RequestReferenceData(DataSource dataSource) {
		this.jdbc = new JdbcTemplate(dataSource);
	}

	List<Currency> currencies() {
		return jdbc.query("SELECT code, name_en, name_fr, minor_units FROM currency ORDER BY code", (rs, i) -> new Currency(
				rs.getString("code"), rs.getString("name_en"), rs.getString("name_fr"), rs.getInt("minor_units")));
	}

	Optional<Currency> currency(String code) {
		return currencies().stream().filter(c -> c.code().equals(code)).findFirst();
	}

	List<Unit> units() {
		return jdbc.query("SELECT code, name_en, name_fr FROM unit_of_measure ORDER BY sort_order",
				(rs, i) -> new Unit(rs.getString("code"), rs.getString("name_en"), rs.getString("name_fr")));
	}

	boolean unitExists(String code) {
		return units().stream().anyMatch(u -> u.code().equals(code));
	}

	List<Category> activeCategories(long groupId) {
		return jdbc.query("SELECT id, code, name_en, name_fr FROM request_category WHERE group_id = ? AND is_active = 1 "
				+ "ORDER BY name_en", (rs, i) -> new Category(rs.getLong("id"), rs.getString("code"),
						rs.getString("name_en"), rs.getString("name_fr")), groupId);
	}

	Optional<Category> category(long groupId, long categoryId) {
		return jdbc.query("SELECT id, code, name_en, name_fr FROM request_category WHERE group_id = ? AND id = ?",
				(rs, i) -> new Category(rs.getLong("id"), rs.getString("code"), rs.getString("name_en"),
						rs.getString("name_fr")), groupId, categoryId).stream().findFirst();
	}

	boolean isActiveCategory(long groupId, long categoryId) {
		return activeCategories(groupId).stream().anyMatch(c -> c.id() == categoryId);
	}

	record Currency(String code, String nameEn, String nameFr, int minorUnits) {
	}

	record Unit(String code, String nameEn, String nameFr) {
	}

	record Category(long id, String code, String nameEn, String nameFr) {
	}

}

package com.epi.procurement.orgadmin;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.epi.procurement.identity.CurrentUser;
import com.epi.procurement.identity.CurrentUserProvider;
import com.epi.procurement.identity.Role;
import com.epi.procurement.organization.OrganizationAdmin;
import com.epi.procurement.platform.web.Idempotency;

@RestController
@RequestMapping("/api/v1/admin/organization")
class OrganizationAdminController {

	private final OrganizationAdmin admin;

	private final CurrentUserProvider currentUser;

	private final Idempotency idempotency;

	OrganizationAdminController(OrganizationAdmin admin, CurrentUserProvider currentUser, Idempotency idempotency) {
		this.admin = admin;
		this.currentUser = currentUser;
		this.idempotency = idempotency;
	}

	@GetMapping("/companies")
	List<OrganizationAdmin.CompanyRow> companies() {
		return admin.companies(actor(currentUser.require()));
	}

	@PostMapping("/companies")
	ResponseEntity<?> createCompany(@RequestBody OrganizationAdmin.CompanyBody body,
			@RequestHeader(name = Idempotency.HEADER, required = false) String key) {
		CurrentUser user = currentUser.require();
		return idempotency.execute("user:" + user.id(), key, "org-company|" + body,
				() -> ResponseEntity.status(201).body(admin.createCompany(actor(user), body)));
	}

	@PutMapping("/companies/{id}")
	ResponseEntity<?> updateCompany(@PathVariable long id, @RequestBody OrganizationAdmin.CompanyBody body,
			@RequestHeader(name = Idempotency.HEADER, required = false) String key) {
		CurrentUser user = currentUser.require();
		return idempotency.execute("user:" + user.id(), key, "org-company-update|" + id + "|" + body,
				() -> ResponseEntity.ok(admin.updateCompany(actor(user), id, body)));
	}

	@GetMapping("/companies/{id}/sites")
	List<OrganizationAdmin.UnitRow> sites(@PathVariable long id) {
		return admin.sites(actor(currentUser.require()), id);
	}

	@PostMapping("/companies/{id}/sites")
	ResponseEntity<?> createSite(@PathVariable long id, @RequestBody OrganizationAdmin.UnitBody body,
			@RequestHeader(name = Idempotency.HEADER, required = false) String key) {
		CurrentUser user = currentUser.require();
		return idempotency.execute("user:" + user.id(), key, "org-site|" + id + "|" + body,
				() -> ResponseEntity.status(201).body(admin.createSite(actor(user), id, body)));
	}

	@GetMapping("/companies/{id}/departments")
	List<OrganizationAdmin.UnitRow> departments(@PathVariable long id) {
		return admin.departments(actor(currentUser.require()), id);
	}

	@PostMapping("/companies/{id}/departments")
	ResponseEntity<?> createDepartment(@PathVariable long id, @RequestBody OrganizationAdmin.UnitBody body,
			@RequestHeader(name = Idempotency.HEADER, required = false) String key) {
		CurrentUser user = currentUser.require();
		return idempotency.execute("user:" + user.id(), key, "org-dept|" + id + "|" + body,
				() -> ResponseEntity.status(201).body(admin.createDepartment(actor(user), id, body)));
	}

	@GetMapping("/companies/{id}/cost-centres")
	List<OrganizationAdmin.UnitRow> costCentres(@PathVariable long id) {
		return admin.costCenters(actor(currentUser.require()), id);
	}

	@PostMapping("/companies/{id}/cost-centres")
	ResponseEntity<?> createCostCentre(@PathVariable long id, @RequestBody OrganizationAdmin.UnitBody body,
			@RequestHeader(name = Idempotency.HEADER, required = false) String key) {
		CurrentUser user = currentUser.require();
		return idempotency.execute("user:" + user.id(), key, "org-cc|" + id + "|" + body,
				() -> ResponseEntity.status(201).body(admin.createCostCenter(actor(user), id, body)));
	}

	private static OrganizationAdmin.Actor actor(CurrentUser user) {
		return new OrganizationAdmin.Actor(user.id(), user.displayName(), user.companyId(), user.groupId(),
				user.hasRole(Role.ADMIN));
	}
}

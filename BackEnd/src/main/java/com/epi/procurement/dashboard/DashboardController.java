package com.epi.procurement.dashboard;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.epi.procurement.identity.CurrentUserProvider;

@RestController
@RequestMapping("/api/v1/dashboards")
class DashboardController {

	private final DashboardService dashboards;

	private final ErpExportService erpExport;

	private final CurrentUserProvider currentUser;

	DashboardController(DashboardService dashboards, ErpExportService erpExport, CurrentUserProvider currentUser) {
		this.dashboards = dashboards;
		this.erpExport = erpExport;
		this.currentUser = currentUser;
	}

	@GetMapping("/pipeline")
	PipelineView pipeline() {
		return dashboards.pipeline(currentUser.require());
	}

	@GetMapping("/management")
	ManagementPage management(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return dashboards.management(currentUser.require(), page, size);
	}

	@GetMapping("/requests/{requestId}/audit-pack")
	ResponseEntity<byte[]> auditPack(@PathVariable long requestId) {
		byte[] pdf = dashboards.auditPackPdf(currentUser.require(), requestId);
		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-pack-" + requestId + ".pdf\"")
			.contentType(MediaType.APPLICATION_PDF)
			.body(pdf);
	}

	@GetMapping("/exports/requests.csv")
	ResponseEntity<byte[]> exportRequests() {
		byte[] csv = erpExport.exportRequestsCsv(currentUser.require());
		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"erp-requests.csv\"")
			.contentType(new MediaType("text", "csv"))
			.body(csv);
	}

	@GetMapping("/exports/purchase-orders.csv")
	ResponseEntity<byte[]> exportPurchaseOrders() {
		byte[] csv = erpExport.exportPurchaseOrdersCsv(currentUser.require());
		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"erp-purchase-orders.csv\"")
			.contentType(new MediaType("text", "csv"))
			.body(csv);
	}

}

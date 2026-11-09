package com.epi.procurement.receipt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;

import com.epi.procurement.support.ApiClient;
import com.epi.procurement.support.SqlServerTestSupport;

import tools.jackson.databind.json.JsonMapper;

class ReceiptIntegrationTests extends SqlServerTestSupport {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");

	@Test
	void deliverThenPartialReceiptConfirmInvoiceAndClose() {
		long id = delivered("Receipt seals");
		ApiClient omar = signedIn("receiver.tn@demo.local");
		signedIn("requester.fr@demo.local").get("/api/v1/receipts/requests/" + id).expectStatus(404);

		int version = ((Number) omar.get("/api/v1/requests/" + id).json("$.version")).intValue();
		long poLineId = jdbc.queryForObject(
				"SELECT id FROM purchase_order_line WHERE purchase_order_id = (SELECT id FROM purchase_order WHERE request_id = ?)",
				Long.class, id);
		ApiClient.Response receipt = omar.post("/api/v1/receipts/requests/" + id + "/goods-receipts",
				JSON.writeValueAsString(Map.of("requestVersion", version, "note", "Gate check", "lines",
						List.of(Map.of("purchaseOrderLineId", poLineId, "quantityReceived", 1, "quantityAccepted", 1,
								"quantityRejected", 0, "quantityDamaged", 0, "condition", "OK")),
						"evidence",
						List.of(Map.of("kind", "PHOTO", "referenceNumber", "CAM-1", "note", "Reference only")))))
			.expectStatus(200);
		assertThat((String) receipt.json("$.number")).matches("DEMO-.+-GR-\\d{4}-\\d{6}");
		assertThat((String) samiStatus(id)).isIn("PARTIALLY_RECEIVED", "NON_CONFORMING");

		long receiptId = ((Number) receipt.json("$.id")).longValue();
		ApiClient sami = signedIn("requester.tn@demo.local");
		sami.post("/api/v1/receipts/goods-receipts/" + receiptId + "/confirm",
				"{\"version\":" + receipt.json("$.version") + "}").expectStatus(200);
		assertThat((String) samiStatus(id)).isEqualTo("ACCEPTED");

		ApiClient ines = signedIn("buyer@demo.local");
		version = ((Number) ines.get("/api/v1/requests/" + id).json("$.version")).intValue();
		ines.post("/api/v1/receipts/requests/" + id + "/invoice",
				JSON.writeValueAsString(Map.of("requestVersion", version, "invoiceNumber", "INV-100", "amount", 35,
						"currency", "EUR", "documentReference", "PDF-REF-1", "matchAttested", true, "paymentStatus",
						"PENDING")))
			.expectStatus(200);
		assertThat((String) samiStatus(id)).isEqualTo("INVOICED");

		ApiClient mehdi = signedIn("purchasing.manager@demo.local");
		version = ((Number) mehdi.get("/api/v1/requests/" + id).json("$.version")).intValue();
		mehdi.post("/api/v1/receipts/requests/" + id + "/close",
				JSON.writeValueAsString(Map.of("requestVersion", version, "rating", 5, "scoreComment", "On time")))
			.expectStatus(200);
		assertThat((String) samiStatus(id)).isEqualTo("CLOSED");
		assertThat((String) samiUserStatus(id)).isEqualTo("CLOSED");
	}

	@Test
	void cannotReceiveBeforeDeliveryOrOverQuantity() {
		long id = confirmedPo("Too early receipt");
		// Requester can see the dossier before delivery; receiver visibility starts at shipment stage.
		ApiClient sami = signedIn("requester.tn@demo.local");
		int version = ((Number) sami.get("/api/v1/requests/" + id).json("$.version")).intValue();
		sami.post("/api/v1/receipts/requests/" + id + "/goods-receipts", "{\"requestVersion\":" + version + "}")
			.expectStatus(409);
		signedIn("receiver.tn@demo.local").get("/api/v1/requests/" + id).expectStatus(404);

		ApiClient omar = signedIn("receiver.tn@demo.local");
		long ready = delivered("Over-receive");
		version = ((Number) omar.get("/api/v1/requests/" + ready).json("$.version")).intValue();
		long poLineId = jdbc.queryForObject(
				"SELECT id FROM purchase_order_line WHERE purchase_order_id = (SELECT id FROM purchase_order WHERE request_id = ?)",
				Long.class, ready);
		omar.post("/api/v1/receipts/requests/" + ready + "/goods-receipts",
				JSON.writeValueAsString(Map.of("requestVersion", version, "lines",
						List.of(Map.of("purchaseOrderLineId", poLineId, "quantityReceived", 2, "quantityAccepted", 2,
								"quantityRejected", 0, "quantityDamaged", 0, "condition", "OK")))))
			.expectStatus(409);
	}

	@Test
	void concurrentReceiptOnlyOneFullSucceeds() throws Exception {
		long id = delivered("Concurrent receipt");
		int version = ((Number) signedIn("receiver.tn@demo.local").get("/api/v1/requests/" + id).json("$.version"))
			.intValue();
		String body = "{\"requestVersion\":" + version + "}";
		var pool = Executors.newFixedThreadPool(2);
		try {
			Callable<Integer> call = () -> signedIn("receiver.tn@demo.local")
				.post("/api/v1/receipts/requests/" + id + "/goods-receipts", body)
				.status();
			var a = pool.submit(call);
			var b = pool.submit(call);
			List<Integer> statuses = List.of(a.get(), b.get());
			assertThat(statuses).contains(200);
			assertThat(statuses.stream().filter(s -> s != 200).findFirst().orElse(200)).isIn(409, 400);
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM goods_receipt WHERE request_id = ?", Integer.class, id))
				.isEqualTo(1);
		}
		finally {
			pool.shutdownNow();
		}
	}

	@Test
	void invalidReceiptQuantitiesAreRejectedAsValidationErrors() {
		long id = delivered("Invalid quantities");
		ApiClient omar = signedIn("receiver.tn@demo.local");
		int version = ((Number) omar.get("/api/v1/requests/" + id).json("$.version")).intValue();
		long poLineId = poLineId(id);
		omar.post("/api/v1/receipts/requests/" + id + "/goods-receipts",
				JSON.writeValueAsString(Map.of("requestVersion", version, "lines",
						List.of(Map.of("purchaseOrderLineId", poLineId, "quantityReceived", 1, "quantityAccepted", 5,
								"quantityRejected", -4, "quantityDamaged", 0, "condition", "OK")))))
			.expectStatus(422);
		omar.post("/api/v1/receipts/requests/" + id + "/goods-receipts",
				JSON.writeValueAsString(Map.of("requestVersion", version, "lines",
						List.of(Map.of("purchaseOrderLineId", poLineId, "quantityReceived", new java.math.BigDecimal("0.00001"),
								"quantityAccepted", 0, "quantityRejected", 0, "quantityDamaged", 0, "condition", "OK")))))
			.expectStatus(422);
	}

	@Test
	void concurrentReceiptConfirmationAuditsOnce() throws Exception {
		long id = delivered("Concurrent confirm");
		ApiClient omar = signedIn("receiver.tn@demo.local");
		int version = ((Number) omar.get("/api/v1/requests/" + id).json("$.version")).intValue();
		ApiClient.Response receipt = omar.post("/api/v1/receipts/requests/" + id + "/goods-receipts",
				"{\"requestVersion\":" + version + "}").expectStatus(200);
		long receiptId = ((Number) receipt.json("$.id")).longValue();
		var pool = Executors.newFixedThreadPool(4);
		try {
			Callable<Integer> call = () -> signedIn("requester.tn@demo.local")
				.post("/api/v1/receipts/goods-receipts/" + receiptId + "/confirm", "{}")
				.status();
			List<java.util.concurrent.Future<Integer>> calls = pool.invokeAll(List.of(call, call, call, call));
			List<Integer> statuses = new ArrayList<>();
			for (var c : calls) {
				statuses.add(c.get());
			}
			assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(1);
			assertThat(jdbc.queryForObject("""
					SELECT COUNT(*) FROM audit_event WHERE entity_type = 'PURCHASE_REQUEST' AND entity_id = ?
					AND action = 'RECEIPT_CONFIRMED'""", Integer.class, Long.toString(id))).isEqualTo(1);
		}
		finally {
			pool.shutdownNow();
		}
	}

	@Test
	void onlyAPurchasingManagerMayWaiveAnOutstandingQuantity() {
		long id = invoiced("Waiver rights");
		jdbc.update("""
				UPDATE grl SET quantity_accepted = 0.5 FROM goods_receipt_line grl
				JOIN goods_receipt gr ON gr.id = grl.goods_receipt_id WHERE gr.request_id = ?""", id);
		ApiClient sami = signedIn("requester.tn@demo.local");
		int version = ((Number) sami.get("/api/v1/requests/" + id).json("$.version")).intValue();
		sami.post("/api/v1/receipts/requests/" + id + "/close",
				JSON.writeValueAsString(Map.of("requestVersion", version, "waiverNote", "Requester waives")))
			.expectStatus(409);
		signedIn("purchasing.manager@demo.local").post("/api/v1/receipts/requests/" + id + "/close",
				JSON.writeValueAsString(Map.of("requestVersion", version, "waiverNote", "Manager waives")))
			.expectStatus(200);
	}

	@Test
	void invoiceRejectsUnknownCurrencyAndRoundsToMinorUnits() {
		long id = accepted("Invoice precision");
		ApiClient ines = signedIn("buyer@demo.local");
		int version = ((Number) ines.get("/api/v1/requests/" + id).json("$.version")).intValue();
		ines.post("/api/v1/receipts/requests/" + id + "/invoice", JSON.writeValueAsString(Map.of("requestVersion", version,
				"invoiceNumber", "INV-X", "amount", 35, "currency", "ZZZZ", "matchAttested", true)))
			.expectStatus(422);
		ines.post("/api/v1/receipts/requests/" + id + "/invoice",
				JSON.writeValueAsString(Map.of("requestVersion", version, "invoiceNumber", "INV-Y", "amount",
						new java.math.BigDecimal("35.005"), "currency", "EUR", "matchAttested", true)))
			.expectStatus(200);
		assertThat(jdbc.queryForObject("SELECT amount FROM supplier_invoice WHERE request_id = ?",
				java.math.BigDecimal.class, id)).isEqualByComparingTo("35.01");
	}

	@Test
	void aManagerAcceptsARejectedUnitAndTheRequestReachesClosure() {
		long id = rejected("Shortfall path");
		ApiClient sami = signedIn("requester.tn@demo.local");
		ApiClient mehdi = signedIn("purchasing.manager@demo.local");
		int version = ((Number) mehdi.get("/api/v1/requests/" + id).json("$.version")).intValue();
		String body = JSON.writeValueAsString(Map.of("requestVersion", version, "reason", "Rejected unit credited"));
		signedIn("requester.fr@demo.local").post("/api/v1/receipts/requests/" + id + "/shortfall-acceptance", body)
			.expectStatus(404);
		sami.post("/api/v1/receipts/requests/" + id + "/shortfall-acceptance", body).expectStatus(403);
		mehdi.post("/api/v1/receipts/requests/" + id + "/shortfall-acceptance",
				JSON.writeValueAsString(Map.of("requestVersion", version, "reason", " "))).expectStatus(422);

		mehdi.post("/api/v1/receipts/requests/" + id + "/shortfall-acceptance", body).expectStatus(200);
		assertThat((String) samiStatus(id)).isEqualTo("ACCEPTED");
		assertThat(jdbc.queryForObject("""
				SELECT COUNT(*) FROM audit_event WHERE entity_type = 'PURCHASE_REQUEST' AND entity_id = ?
				AND action = 'SHORTFALL_ACCEPTED'""", Integer.class, Long.toString(id))).isEqualTo(1);

		ApiClient ines = signedIn("buyer@demo.local");
		version = ((Number) ines.get("/api/v1/requests/" + id).json("$.version")).intValue();
		ines.post("/api/v1/receipts/requests/" + id + "/invoice", JSON.writeValueAsString(Map.of("requestVersion", version,
				"invoiceNumber", "INV-S", "amount", 35, "currency", "EUR", "matchAttested", true))).expectStatus(200);
		version = ((Number) sami.get("/api/v1/requests/" + id).json("$.version")).intValue();
		sami.post("/api/v1/receipts/requests/" + id + "/close", "{\"requestVersion\":" + version + "}").expectStatus(200);
		assertThat((String) samiStatus(id)).isEqualTo("CLOSED");
	}

	@Test
	void shortfallAcceptanceWaitsForEveryReceiptToBeConfirmed() {
		long id = delivered("Shortfall unconfirmed");
		ApiClient omar = signedIn("receiver.tn@demo.local");
		int version = ((Number) omar.get("/api/v1/requests/" + id).json("$.version")).intValue();
		omar.post("/api/v1/receipts/requests/" + id + "/goods-receipts",
				JSON.writeValueAsString(Map.of("requestVersion", version, "lines",
						List.of(Map.of("purchaseOrderLineId", poLineId(id), "quantityReceived", 1, "quantityAccepted", 0,
								"quantityRejected", 1, "quantityDamaged", 0, "condition", "NON_CONFORMING")))))
			.expectStatus(200);
		ApiClient mehdi = signedIn("purchasing.manager@demo.local");
		version = ((Number) mehdi.get("/api/v1/requests/" + id).json("$.version")).intValue();
		mehdi.post("/api/v1/receipts/requests/" + id + "/shortfall-acceptance",
				JSON.writeValueAsString(Map.of("requestVersion", version, "reason", "Too early"))).expectStatus(409);
	}

	@Test
	void aPendingInvoiceCanBeMarkedPaidOnceByPurchasing() {
		long id = invoiced("Payment update");
		ApiClient ines = signedIn("buyer@demo.local");
		int version = ((Number) ines.get("/api/v1/requests/" + id).json("$.version")).intValue();
		String body = "{\"requestVersion\":" + version + "}";
		signedIn("requester.fr@demo.local").post("/api/v1/receipts/requests/" + id + "/invoice/paid", body).expectStatus(404);
		signedIn("requester.tn@demo.local").post("/api/v1/receipts/requests/" + id + "/invoice/paid", body).expectStatus(403);

		ines.post("/api/v1/receipts/requests/" + id + "/invoice/paid", body).expectStatus(200);
		assertThat((String) samiStatus(id)).isEqualTo("PAID");
		version = ((Number) ines.get("/api/v1/requests/" + id).json("$.version")).intValue();
		ines.post("/api/v1/receipts/requests/" + id + "/invoice/paid", "{\"requestVersion\":" + version + "}").expectStatus(409);
		assertThat((String) ines.get("/api/v1/receipts/requests/" + id).json("$.invoice.paymentStatus")).isEqualTo("PAID");
	}

	@Test
	void dossierExposesPurchaseOrderCurrencyAndRemainingQuantity() {
		long id = delivered("Dossier order");
		ApiClient.Response dossier = signedIn("buyer@demo.local").get("/api/v1/receipts/requests/" + id).expectStatus(200);
		assertThat((String) dossier.json("$.purchaseOrder.currency")).isEqualTo("EUR");
		assertThat(((Number) dossier.json("$.purchaseOrder.lines[0].received")).doubleValue()).isZero();
	}

	private long rejected(String title) {
		long id = delivered(title);
		ApiClient omar = signedIn("receiver.tn@demo.local");
		int version = ((Number) omar.get("/api/v1/requests/" + id).json("$.version")).intValue();
		ApiClient.Response receipt = omar.post("/api/v1/receipts/requests/" + id + "/goods-receipts",
				JSON.writeValueAsString(Map.of("requestVersion", version, "lines",
						List.of(Map.of("purchaseOrderLineId", poLineId(id), "quantityReceived", 1, "quantityAccepted", 0,
								"quantityRejected", 1, "quantityDamaged", 0, "condition", "NON_CONFORMING")))))
			.expectStatus(200);
		signedIn("requester.tn@demo.local").post("/api/v1/receipts/goods-receipts/" + receipt.json("$.id") + "/confirm", "{}")
			.expectStatus(200);
		assertThat((String) samiStatus(id)).isEqualTo("NON_CONFORMING");
		return id;
	}

	@Test
	void logisticsInboxPagesInTheDatabaseAndStaysScoped() {
		confirmedPo("Inbox one");
		confirmedPo("Inbox two");
		ApiClient ines = signedIn("buyer@demo.local");
		ApiClient.Response first = ines.get("/api/v1/logistics/inbox?page=0&size=1").expectStatus(200);
		ApiClient.Response second = ines.get("/api/v1/logistics/inbox?page=1&size=1").expectStatus(200);
		long expected = jdbc.queryForObject("""
				SELECT COUNT(*) FROM purchase_order WHERE status = 'CONFIRMED' AND buying_company_id IN (
				    SELECT company_id FROM user_role WHERE user_id = ? AND role IN ('BUYER', 'PURCHASING_MANAGER'))""",
				Long.class, userId("buyer@demo.local"));
		assertThat(((Number) first.json("$.totalItems")).longValue()).isEqualTo(expected);
		assertThat((List<?>) first.json("$.items")).hasSize(1);
		assertThat((Object) first.json("$.items[0].poId")).isNotEqualTo(second.json("$.items[0].poId"));
		signedIn("requester.tn@demo.local").get("/api/v1/logistics/inbox").expectStatus(403);

		// A purchase order of another buying company leaves this buyer's inbox.
		long moved = ((Number) first.json("$.items[0].poId")).longValue();
		long otherCompany = jdbc.queryForObject("""
				SELECT TOP 1 id FROM company WHERE code = 'DEMO-FR-PLT'""", Long.class);
		jdbc.update("UPDATE purchase_order SET buying_company_id = ? WHERE id = ?", otherCompany, moved);
		ApiClient.Response after = ines.get("/api/v1/logistics/inbox?page=0&size=100").expectStatus(200);
		assertThat(((Number) after.json("$.totalItems")).longValue()).isEqualTo(expected - 1);
		assertThat((List<Number>) after.json("$.items[*].poId")).extracting(Number::longValue).doesNotContain(moved);
	}

	@Test
	void anUnattestedInvoiceCanBeAttestedAndThenClosed() {
		long id = accepted("Attest later");
		ApiClient ines = signedIn("buyer@demo.local");
		int version = ((Number) ines.get("/api/v1/requests/" + id).json("$.version")).intValue();
		ines.post("/api/v1/receipts/requests/" + id + "/invoice", JSON.writeValueAsString(Map.of("requestVersion", version,
				"invoiceNumber", "INV-L", "amount", 35, "currency", "EUR", "matchAttested", false))).expectStatus(200);
		ApiClient sami = signedIn("requester.tn@demo.local");
		version = ((Number) sami.get("/api/v1/requests/" + id).json("$.version")).intValue();
		sami.post("/api/v1/receipts/requests/" + id + "/close", "{\"requestVersion\":" + version + "}").expectStatus(409);

		signedIn("requester.fr@demo.local").post("/api/v1/receipts/requests/" + id + "/invoice/match-attested",
				"{\"requestVersion\":" + version + "}").expectStatus(404);
		sami.post("/api/v1/receipts/requests/" + id + "/invoice/match-attested", "{\"requestVersion\":" + version + "}")
			.expectStatus(403);
		ines.post("/api/v1/receipts/requests/" + id + "/invoice/match-attested", "{\"requestVersion\":" + version + "}")
			.expectStatus(200);
		version = ((Number) ines.get("/api/v1/requests/" + id).json("$.version")).intValue();
		ines.post("/api/v1/receipts/requests/" + id + "/invoice/match-attested", "{\"requestVersion\":" + version + "}")
			.expectStatus(409);
		sami.post("/api/v1/receipts/requests/" + id + "/close", "{\"requestVersion\":" + version + "}").expectStatus(200);
		assertThat((String) samiStatus(id)).isEqualTo("CLOSED");
	}

	@Test
	void dossierLimitsReceivableQuantityToWhatWasShipped() {
		long id = delivered("Receivable cap");
		long poLine = poLineId(id);
		jdbc.update("UPDATE purchase_order_line SET quantity = 4 WHERE id = ?", poLine);
		ApiClient.Response dossier = signedIn("buyer@demo.local").get("/api/v1/receipts/requests/" + id).expectStatus(200);
		assertThat(((Number) dossier.json("$.purchaseOrder.lines[0].quantity")).doubleValue()).isEqualTo(4d);
		assertThat(((Number) dossier.json("$.purchaseOrder.lines[0].receivable")).doubleValue()).isEqualTo(1d);
	}

	private long poLineId(long id) {
		return jdbc.queryForObject(
				"SELECT id FROM purchase_order_line WHERE purchase_order_id = (SELECT id FROM purchase_order WHERE request_id = ?)",
				Long.class, id);
	}

	private long accepted(String title) {
		long id = delivered(title);
		ApiClient omar = signedIn("receiver.tn@demo.local");
		int version = ((Number) omar.get("/api/v1/requests/" + id).json("$.version")).intValue();
		ApiClient.Response receipt = omar.post("/api/v1/receipts/requests/" + id + "/goods-receipts",
				"{\"requestVersion\":" + version + "}").expectStatus(200);
		signedIn("requester.tn@demo.local").post("/api/v1/receipts/goods-receipts/" + receipt.json("$.id") + "/confirm", "{}")
			.expectStatus(200);
		return id;
	}

	private long invoiced(String title) {
		long id = accepted(title);
		ApiClient ines = signedIn("buyer@demo.local");
		int version = ((Number) ines.get("/api/v1/requests/" + id).json("$.version")).intValue();
		ines.post("/api/v1/receipts/requests/" + id + "/invoice", JSON.writeValueAsString(Map.of("requestVersion", version,
				"invoiceNumber", "INV-W", "amount", 35, "currency", "EUR", "matchAttested", true)))
			.expectStatus(200);
		return id;
	}

	private String samiStatus(long id) {
		return signedIn("requester.tn@demo.local").get("/api/v1/requests/" + id).json("$.status");
	}

	private String samiUserStatus(long id) {
		return signedIn("requester.tn@demo.local").get("/api/v1/requests/" + id).json("$.userStatus");
	}

	private long delivered(String title) {
		long id = confirmedPo(title);
		ApiClient ines = signedIn("buyer@demo.local");
		int version = ((Number) ines.get("/api/v1/requests/" + id).json("$.version")).intValue();
		LocalDate planned = LocalDate.now(TUNIS).minusDays(1);
		ApiClient.Response shipment = ines.post("/api/v1/logistics/requests/" + id + "/shipments",
				JSON.writeValueAsString(Map.of("requestVersion", version, "kind", "LOCAL", "milestonePlans",
						List.of(Map.of("code", "DELIVERED", "plannedDate", planned.toString())))))
			.expectStatus(200);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> milestones = shipment.json("$.milestones");
		Map<String, Object> delivered = milestones.stream()
			.filter(m -> "DELIVERED".equals(m.get("code")))
			.findFirst()
			.orElseThrow();
		int v = ((Number) shipment.json("$.version")).intValue();
		ines.put("/api/v1/logistics/shipments/" + shipment.json("$.id") + "/milestones/" + delivered.get("id"),
				"{\"version\":" + v + ",\"actualDate\":\"" + LocalDate.now(TUNIS) + "\"}").expectStatus(200);
		assertThat((String) samiStatus(id)).isEqualTo("DELIVERED");
		return id;
	}

	private long confirmedPo(String title) {
		long id = issuedOnly(title);
		ApiClient ines = signedIn("buyer@demo.local");
		ApiClient.Response po = ines.get("/api/v1/ordering/requests/" + id + "/purchase-order").expectStatus(200);
		ines.post("/api/v1/ordering/purchase-orders/" + po.json("$.id") + "/confirm",
				"{\"version\":" + po.json("$.version") + ",\"confirmationNote\":\"OK\"}").expectStatus(200);
		return id;
	}

	private long issuedOnly(String title) {
		long id = commerciallyValidated(title);
		ApiClient ines = signedIn("buyer@demo.local");
		long quoteId = ((Number) ines.get("/api/v1/sourcing/requests/" + id + "/rfq").json("$.quotes[0].id"))
			.longValue();
		int version = ((Number) ines.get("/api/v1/requests/" + id).json("$.version")).intValue();
		ines.post("/api/v1/ordering/requests/" + id + "/award",
				"{\"requestVersion\":" + version + ",\"quotationId\":" + quoteId + ",\"reason\":\"Receipt award\"}")
			.expectStatus(200);
		version = ((Number) ines.get("/api/v1/requests/" + id).json("$.version")).intValue();
		ApiClient.Response po = ines.post("/api/v1/ordering/requests/" + id + "/purchase-order",
				"{\"requestVersion\":" + version + "}").expectStatus(200);
		ines.post("/api/v1/ordering/purchase-orders/" + po.json("$.id") + "/issue",
				"{\"version\":" + po.json("$.version") + "}").expectStatus(200);
		return id;
	}

	private long commerciallyValidated(String title) {
		ApiClient sami = signedIn("requester.tn@demo.local");
		long id = accept(assign(approve(submit(sami, request(title)))));
		ApiClient ines = signedIn("buyer@demo.local");
		long skf = jdbc.queryForObject("SELECT id FROM supplier WHERE code = 'DEMO-SKF'", Long.class);
		ApiClient.Response rfq = ines.post("/api/v1/sourcing/requests/" + id + "/rfq",
				"{\"kind\":\"QUICK\",\"supplierIds\":[" + skf + "]}").expectStatus(200);
		long rfqId = ((Number) rfq.json("$.id")).longValue();
		long rfqSupplierId = ((Number) rfq.json("$.suppliers[0].id")).longValue();
		long rfqLineId = ((Number) rfq.json("$.lines[0].id")).longValue();
		ines.post("/api/v1/sourcing/rfqs/" + rfqId + "/quotes", JSON.writeValueAsString(Map.of("rfqSupplierId",
				rfqSupplierId, "currency", "EUR", "freightAmount", 10, "dutyAmount", 0, "otherAmount", 0, "lines",
				List.of(Map.of("rfqLineId", rfqLineId, "quantity", 1, "unitPrice", 25))))).expectStatus(200);
		int version = ((Number) ines.get("/api/v1/requests/" + id).json("$.version")).intValue();
		ines.post("/api/v1/sourcing/requests/" + id + "/validate-technical",
				"{\"requestVersion\":" + version + ",\"comment\":\"Fits\"}").expectStatus(200);
		version = ((Number) ines.get("/api/v1/requests/" + id).json("$.version")).intValue();
		ines.post("/api/v1/sourcing/requests/" + id + "/validate-commercial",
				"{\"requestVersion\":" + version + ",\"comment\":\"Price OK\"}").expectStatus(200);
		return id;
	}

	private long accept(long requestId) {
		ApiClient ines = signedIn("buyer@demo.local");
		int version = ((Number) ines.get("/api/v1/requests/" + requestId).json("$.version")).intValue();
		ines.post("/api/v1/purchasing/requests/" + requestId + "/accept", "{\"version\":" + version + "}")
			.expectStatus(200);
		return requestId;
	}

	private long assign(long requestId) {
		ApiClient mehdi = signedIn("purchasing.manager@demo.local");
		int version = ((Number) mehdi.get("/api/v1/requests/" + requestId).json("$.version")).intValue();
		mehdi.post("/api/v1/purchasing/requests/" + requestId + "/assign",
				"{\"version\":" + version + ",\"buyerUserId\":" + userId("buyer@demo.local") + "}").expectStatus(200);
		return requestId;
	}

	private long approve(long requestId) {
		ApiClient karim = signedIn("manager.tn@demo.local");
		ApiClient.Response approval = karim.get("/api/v1/requests/" + requestId + "/approval").expectStatus(200);
		karim.post("/api/v1/approvals/tasks/" + approval.json("$.myTask.id") + "/approve",
				"{\"version\":" + approval.json("$.myTask.version") + "}").expectStatus(200);
		return requestId;
	}

	private long submit(ApiClient client, Map<String, Object> body) {
		ApiClient.Response draft = client.post("/api/v1/requests", json(body)).expectStatus(201);
		return client.post("/api/v1/requests/" + draft.id() + "/submit", "{\"version\":" + draft.version() + "}")
			.expectStatus(200)
			.id();
	}

	private Map<String, Object> request(String title) {
		long tn = companyId("DEMO-TN-PLT");
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("title", title);
		body.put("categoryId", jdbc.queryForObject(
				"SELECT c.id FROM request_category c JOIN organization_group g ON g.id = c.group_id WHERE g.code = 'DEMO-GRP' AND c.code = 'DEMO-SPARE'",
				Long.class));
		body.put("priority", "NORMAL");
		body.put("purchaseType", "LOCAL");
		body.put("currency", "TND");
		body.put("departmentId", jdbc.queryForObject("SELECT id FROM department WHERE company_id = ? AND code = 'DEMO-MAINT'",
				Long.class, tn));
		body.put("requiredDate", LocalDate.now(TUNIS).plusDays(21).toString());
		body.put("lines", new ArrayList<>(List.of(Map.of("description", title, "quantity", 1, "unit", "PCE",
				"estimatedUnitPrice", 20))));
		return body;
	}

	private static String json(Object value) {
		return JSON.writeValueAsString(value);
	}

}

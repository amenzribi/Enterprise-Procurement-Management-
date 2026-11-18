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
						"currency", "EUR", "documentReference", "PDF-REF-1", "matchAttested", false, "paymentStatus",
						"PENDING")))
			.expectStatus(200);
		assertThat((String) samiStatus(id)).isEqualTo("INVOICED");

		ApiClient mehdi = signedIn("purchasing.manager@demo.local");
		attestByManager(id);
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
				"invoiceNumber", "INV-X", "amount", 35, "currency", "ZZZZ", "matchAttested", false)))
			.expectStatus(422);
		ines.post("/api/v1/receipts/requests/" + id + "/invoice",
				JSON.writeValueAsString(Map.of("requestVersion", version, "invoiceNumber", "INV-Y", "amount",
						new java.math.BigDecimal("35.005"), "currency", "EUR", "matchAttested", false)))
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
				"invoiceNumber", "INV-S", "amount", 35, "currency", "EUR", "matchAttested", false))).expectStatus(200);
		attestByManager(id);
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
		ApiClient mehdi = signedIn("purchasing.manager@demo.local");
		int version = ((Number) ines.get("/api/v1/requests/" + id).json("$.version")).intValue();
		String body = "{\"requestVersion\":" + version + "}";
		signedIn("requester.fr@demo.local").post("/api/v1/receipts/requests/" + id + "/invoice/paid", body).expectStatus(404);
		signedIn("requester.tn@demo.local").post("/api/v1/receipts/requests/" + id + "/invoice/paid", body).expectStatus(403);
		// F-5: the buyer who recorded the invoice does not authorise its payment; a purchasing manager does.
		assertThat(ines.post("/api/v1/receipts/requests/" + id + "/invoice/paid", body).expectStatus(403).code())
			.isEqualTo("payment-needs-manager");

		mehdi.post("/api/v1/receipts/requests/" + id + "/invoice/paid", body).expectStatus(200);
		assertThat((String) samiStatus(id)).isEqualTo("PAID");
		version = ((Number) mehdi.get("/api/v1/requests/" + id).json("$.version")).intValue();
		mehdi.post("/api/v1/receipts/requests/" + id + "/invoice/paid", "{\"requestVersion\":" + version + "}").expectStatus(409);
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
	void theRecorderOfAReceiptCannotConfirmIt() {
		long id = delivered("Two people");
		ApiClient omar = signedIn("receiver.tn@demo.local");
		int version = ((Number) omar.get("/api/v1/requests/" + id).json("$.version")).intValue();
		ApiClient.Response receipt = omar.post("/api/v1/receipts/requests/" + id + "/goods-receipts",
				"{\"requestVersion\":" + version + "}").expectStatus(200);
		String path = "/api/v1/receipts/goods-receipts/" + receipt.json("$.id") + "/confirm";
		assertThat((String) omar.post(path, "{}").expectStatus(403).json("$.code")).isEqualTo("self-confirmation");
		assertThat((String) samiStatus(id)).isEqualTo("PARTIALLY_RECEIVED");
		signedIn("requester.tn@demo.local").post(path, "{}").expectStatus(200);
		assertThat((String) samiStatus(id)).isEqualTo("ACCEPTED");
	}

	@Test
	void anExpiredQuoteCannotBeAwarded() {
		long id = commerciallyValidated("Expired quote");
		ApiClient ines = signedIn("buyer@demo.local");
		long quoteId = ((Number) ines.get("/api/v1/sourcing/requests/" + id + "/rfq").json("$.quotes[0].id")).longValue();
		int version = ((Number) ines.get("/api/v1/requests/" + id).json("$.version")).intValue();
		String body = "{\"requestVersion\":" + version + ",\"quotationId\":" + quoteId + ",\"reason\":\"Best offer\"}";
		jdbc.update("UPDATE quotation SET valid_until = ? WHERE id = ?", LocalDate.now(TUNIS).minusDays(2).toString(), quoteId);
		assertThat((String) ines.post("/api/v1/ordering/requests/" + id + "/award", body).expectStatus(409).json("$.code"))
			.isEqualTo("quote-expired");
		jdbc.update("UPDATE quotation SET valid_until = ? WHERE id = ?", LocalDate.now(TUNIS).plusDays(5).toString(), quoteId);
		ines.post("/api/v1/ordering/requests/" + id + "/award", body).expectStatus(200);
	}

	@Test
	void aQuantityDifferentFromTheRequestNeedsARecordedReason() {
		long id = commerciallyValidated("Quantity differs");
		ApiClient ines = signedIn("buyer@demo.local");
		long quoteId = ((Number) ines.get("/api/v1/sourcing/requests/" + id + "/rfq").json("$.quotes[0].id")).longValue();
		int version = ((Number) ines.get("/api/v1/requests/" + id).json("$.version")).intValue();
		jdbc.update("UPDATE quotation_line SET quantity = 2 WHERE quotation_id = ?", quoteId);
		ines.post("/api/v1/ordering/requests/" + id + "/award",
				"{\"requestVersion\":" + version + ",\"quotationId\":" + quoteId + ",\"reason\":\"Best offer\"}").expectStatus(422);
		ines.post("/api/v1/ordering/requests/" + id + "/award", "{\"requestVersion\":" + version + ",\"quotationId\":" + quoteId
				+ ",\"reason\":\"Best offer\",\"quantityReason\":\"Sold in packs of two\"}").expectStatus(200);
		assertThat(jdbc.queryForObject("""
				SELECT comment FROM audit_event WHERE entity_type = 'PURCHASE_REQUEST' AND entity_id = ?
				AND action = 'AWARD_RECORDED'""", String.class, Long.toString(id))).contains("Sold in packs of two");
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
		// F-5: the recorder does not attest; another purchasing person does.
		assertThat(ines.post("/api/v1/receipts/requests/" + id + "/invoice/match-attested",
				"{\"requestVersion\":" + version + "}").expectStatus(403).code()).isEqualTo("invoice-recorder");
		ApiClient mehdi = signedIn("purchasing.manager@demo.local");
		mehdi.post("/api/v1/receipts/requests/" + id + "/invoice/match-attested", "{\"requestVersion\":" + version + "}")
			.expectStatus(200);
		version = ((Number) mehdi.get("/api/v1/requests/" + id).json("$.version")).intValue();
		mehdi.post("/api/v1/receipts/requests/" + id + "/invoice/match-attested", "{\"requestVersion\":" + version + "}")
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

	@Test
	void anApprovedGoodsLineCannotBeReclassifiedAfterAPurchasingReturn() {
		ApiClient sami = signedIn("requester.tn@demo.local");
		long id = approve(submit(sami, request("Reclassify after approval")));
		ApiClient mehdi = signedIn("purchasing.manager@demo.local");
		mehdi.post("/api/v1/purchasing/requests/" + id + "/completeness-return",
				JSON.writeValueAsString(Map.of("version", version(mehdi, id), "reason", "Add the datasheet"))).expectStatus(200);
		assertThat((String) samiStatus(id)).isEqualTo("PURCHASING_RETURNED");
		long lineId = jdbc.queryForObject("SELECT id FROM purchase_request_line WHERE request_id = ?", Long.class, id);
		Map<String, Object> body = request("Reclassify after approval");
		Map<String, Object> approved = new LinkedHashMap<>(Map.of("id", lineId, "description", "Seal", "quantity", 1, "unit", "PCE",
				"estimatedUnitPrice", 20));

		body.put("version", version(sami, id));
		approved.put("lineKind", "SERVICE");
		body.put("lines", List.of(approved));
		ApiClient.Response refused = sami.put("/api/v1/requests/" + id, json(body)).expectStatus(422);
		assertThat((String) refused.json("$.errors[0].code")).isEqualTo("line-kind-locked");

		approved.put("lineKind", "GOODS");
		body.put("lines", List.of(approved, line("Added as service", "SERVICE", 1)));
		sami.put("/api/v1/requests/" + id, json(body)).expectStatus(422);

		body.put("lines", List.of(line("Replacement of the approved line", "SERVICE", 1)));
		sami.put("/api/v1/requests/" + id, json(body)).expectStatus(422);
		assertThat(jdbc.queryForObject("SELECT line_kind FROM purchase_request_line WHERE id = ?", String.class, lineId)).isEqualTo("GOODS");

		body.put("lines", List.of(approved));
		sami.put("/api/v1/requests/" + id, json(body)).expectStatus(200);
	}

	private long serviceRequestAssigned(String title, String description) {
		ApiClient sami = signedIn("requester.tn@demo.local");
		Map<String, Object> draft = request(title);
		draft.put("lines", List.of(line(description, "SERVICE", 2)));
		return accept(assign(approve(submit(sami, draft))));
	}

	@Test
	void approversAreToldAboutServiceLines() {
		ApiClient sami = signedIn("requester.tn@demo.local");
		Map<String, Object> draft = request("Flagged for approver");
		draft.put("lines", List.of(line("Declared as a service", "SERVICE", 1), line("Seal", "GOODS", 1)));
		long id = submit(sami, draft);
		List<Map<String, Object>> items = signedIn("manager.tn@demo.local").get("/api/v1/approvals/inbox?size=100").expectStatus(200).json("$.items");
		Map<String, Object> item = items.stream().filter(i -> ((Number) i.get("requestId")).longValue() == id).findFirst().orElseThrow();
		assertThat(((Number) item.get("serviceLineCount")).intValue()).isEqualTo(1);
	}

	@Test
	void anOrderCannotBeAwardedUntilPurchasingDecidesTheServiceLines() {
		long id = serviceRequestAssigned("Undecided service", "SKF bearings declared as a service");
		ApiClient ines = signedIn("buyer@demo.local");
		ApiClient.Response refused = awardable(id, ines);
		assertThat(refused.status()).isEqualTo(409);
		assertThat((String) refused.json("$.code")).isEqualTo("classification-unconfirmed");
		assertThat((String) samiStatus(id)).isNotEqualTo("AWARDED");

		ApiClient sami = signedIn("requester.tn@demo.local");
		classify(sami, id, List.of()).expectStatus(403);
		signedIn("requester.fr@demo.local").post("/api/v1/purchasing/requests/" + id + "/line-classification",
				json(Map.of("version", version(sami, id), "reclassifyAsGoods", List.of()))).expectStatus(404);
		classify(ines, id, List.of()).expectStatus(200);
		classify(ines, id, List.of()).expectStatus(409);
		assertThat(jdbc.queryForObject("""
				SELECT COUNT(*) FROM audit_event WHERE entity_type = 'PURCHASE_REQUEST' AND entity_id = ?
				AND action = 'LINE_CLASSIFICATION_DECIDED'""", Integer.class, Long.toString(id))).isEqualTo(1);
		awardable(id, ines).expectStatus(200);
	}

	/** Takes an accepted buyer's request to the point of award and tries it, without classifying. */
	private ApiClient.Response awardable(long id, ApiClient ines) {
		if (!"COMMERCIAL_VALIDATION".equals(samiStatus(id))) {
			long skf = jdbc.queryForObject("SELECT id FROM supplier WHERE code = 'DEMO-SKF'", Long.class);
			ApiClient.Response rfq = ines.post("/api/v1/sourcing/requests/" + id + "/rfq",
					"{\"kind\":\"QUICK\",\"supplierIds\":[" + skf + "]}").expectStatus(200);
			ines.post("/api/v1/sourcing/rfqs/" + rfq.json("$.id") + "/quotes", json(Map.of("rfqSupplierId", rfq.json("$.suppliers[0].id"), "currency", "EUR",
					"freightAmount", 0, "dutyAmount", 0, "otherAmount", 0, "lines",
					List.of(Map.of("rfqLineId", rfq.json("$.lines[0].id"), "quantity", 2, "unitPrice", 5))))).expectStatus(200);
			ines.post("/api/v1/sourcing/requests/" + id + "/validate-technical", "{\"requestVersion\":" + version(ines, id) + ",\"comment\":\"Fits\"}").expectStatus(200);
			ines.post("/api/v1/sourcing/requests/" + id + "/validate-commercial", "{\"requestVersion\":" + version(ines, id) + ",\"comment\":\"Price OK\"}").expectStatus(200);
		}
		long quoteId = ((Number) ines.get("/api/v1/sourcing/requests/" + id + "/rfq").json("$.quotes[0].id")).longValue();
		return ines.post("/api/v1/ordering/requests/" + id + "/award",
				"{\"requestVersion\":" + version(ines, id) + ",\"quotationId\":" + quoteId + ",\"reason\":\"QA award\"}");
	}

	@Test
	void purchasingCanSendAMisclassifiedLineThroughReceiptAsGoods() {
		long id = serviceRequestAssigned("Misclassified", "Physical bearings");
		ApiClient ines = signedIn("buyer@demo.local");
		long line = jdbc.queryForObject("SELECT id FROM purchase_request_line WHERE request_id = ?", Long.class, id);
		classify(ines, id, List.of(999_999_999L)).expectStatus(422);
		classify(ines, id, List.of(line)).expectStatus(200);
		assertThat(jdbc.queryForObject("SELECT line_kind FROM purchase_request_line WHERE id = ?", String.class, line)).isEqualTo("GOODS");
		completeOrder(id);
		ApiClient sami = signedIn("requester.tn@demo.local");
		ApiClient.Response refused = sami.post("/api/v1/receipts/requests/" + id + "/service-acceptance",
				json(Map.of("requestVersion", version(sami, id), "comment", "Self accept")));
		assertThat(refused.status()).isEqualTo(409);
		assertThat((String) refused.json("$.code")).isEqualTo("no-service-lines");
	}

	@Test
	void theRequesterCannotDecideTheirOwnClassificationEvenWithAPurchasingRole() {
		long id = serviceRequestAssigned("Own classification", "Fitting");
		jdbc.update("UPDATE purchase_request SET requester_user_id = ? WHERE id = ?", userId("buyer@demo.local"), id);
		ApiClient.Response refused = classify(signedIn("buyer@demo.local"), id, List.of()).expectStatus(403);
		assertThat((String) refused.json("$.code")).isEqualTo("own-classification");
	}

	@Test
	void changingAConfirmedServiceDescriptionNeedsANewDecision() {
		long id = serviceRequestAssigned("Description change", "Pump overhaul");
		ApiClient ines = signedIn("buyer@demo.local");
		classify(ines, id, List.of()).expectStatus(200);
		ApiClient mehdi = signedIn("purchasing.manager@demo.local");
		mehdi.post("/api/v1/purchasing/requests/" + id + "/completeness-return",
				JSON.writeValueAsString(Map.of("version", version(mehdi, id), "reason", "Clarify"))).expectStatus(200);
		ApiClient sami = signedIn("requester.tn@demo.local");
		long lineId = jdbc.queryForObject("SELECT id FROM purchase_request_line WHERE request_id = ?", Long.class, id);
		Map<String, Object> body = request("Description change");
		body.put("version", version(sami, id));
		body.put("lines", List.of(Map.of("id", lineId, "description", "Physical bearings instead", "quantity", 2, "unit", "PCE", "estimatedUnitPrice", 20)));
		sami.put("/api/v1/requests/" + id, json(body)).expectStatus(200);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM purchase_request_line WHERE id = ? AND service_confirmed_at IS NULL", Integer.class, lineId)).isEqualTo(1);
	}

	@Test
	void anAmbiguousReceiptLineNamesTheMissingShipmentLine() {
		long id = confirmedPo("Ambiguous shipment", List.of(line("Bearing", "GOODS", 5)));
		long poLine = poLineId(id, 0);
		deliver(ship(id, List.of(Map.of("purchaseOrderLineId", poLine, "quantity", 3))));
		deliver(ship(id, List.of(Map.of("purchaseOrderLineId", poLine, "quantity", 2))));
		ApiClient.Response response = receive(signedIn("receiver.tn@demo.local"), id, receiptLine(poLine, 1, 1, 0, "OK")).expectStatus(422);
		assertThat((String) response.json("$.code")).isEqualTo("validation-failed");
		assertThat((String) response.json("$.errors[0].field")).isEqualTo("lines");
		assertThat((String) response.json("$.errors[0].code")).isEqualTo("shipment-line-required");
	}

	@Test
	void omittingTheShipmentLineWorksWhenOnlyOneCanApply() {
		long id = confirmedPo("Inferred shipment", List.of(line("Bearing", "GOODS", 2)));
		deliver(ship(id, null));
		ApiClient.Response receipt = receive(signedIn("receiver.tn@demo.local"), id, receiptLine(poLineId(id, 0), 2, 2, 0, "OK")).expectStatus(200);
		assertThat(((Number) receipt.json("$.lines[0].shipmentLineId")).longValue()).isPositive();
	}

	@Test
	void goodsCannotBeAcceptedAsAServiceOrClosedWithoutAReceipt() {
		long id = confirmedPo("Goods as service");
		ApiClient sami = signedIn("requester.tn@demo.local");
		sami.post("/api/v1/receipts/requests/" + id + "/service-acceptance",
				JSON.writeValueAsString(Map.of("requestVersion", version(sami, id), "comment", "Self accept")))
			.expectStatus(409);
		assertThat((String) samiStatus(id)).isEqualTo("ORDERED");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM service_acceptance WHERE request_id = ?", Integer.class, id))
			.isZero();
		sami.post("/api/v1/receipts/requests/" + id + "/close", "{\"requestVersion\":" + version(sami, id) + "}")
			.expectStatus(409);
	}

	@Test
	void anOldServiceAcceptanceOnGoodsDoesNotAllowClosure() {
		long id = delivered("Legacy service acceptance");
		jdbc.update("""
				INSERT INTO service_acceptance (id, request_id, accepted_by_user_id, accepted_at, performance_ok, comment)
				VALUES (NEXT VALUE FOR seq_service_acceptance, ?, ?, SYSUTCDATETIME(), 1, 'Legacy')""", id,
				userId("requester.tn@demo.local"));
		jdbc.update("UPDATE purchase_request SET status = 'INVOICED' WHERE id = ?", id);
		jdbc.update("""
				INSERT INTO supplier_invoice (id, request_id, purchase_order_id, invoice_number, amount, currency,
				    match_attested, payment_status, recorded_by_user_id, recorded_at, version)
				SELECT NEXT VALUE FOR seq_supplier_invoice, request_id, id, 'INV-L', 35, 'EUR', 1, 'PENDING', ?, SYSUTCDATETIME(), 0
				FROM purchase_order WHERE request_id = ?""", userId("buyer@demo.local"), id);
		ApiClient mehdi = signedIn("purchasing.manager@demo.local");
		mehdi.post("/api/v1/receipts/requests/" + id + "/close",
				JSON.writeValueAsString(Map.of("requestVersion", version(mehdi, id), "waiverNote", "Waive it")))
			.expectStatus(409);
		List<String> missing = mehdi.get("/api/v1/receipts/requests/" + id).json("$.closure.missing");
		assertThat(missing).containsExactly("goods-receipt");
	}

	@Test
	void aServiceOrderIsAcceptedWithoutShipmentThenInvoicedAndClosed() {
		long id = confirmedPo("Service only", List.of(line("Pump overhaul", "SERVICE", 1)));
		ApiClient ines = signedIn("buyer@demo.local");
		ines.post("/api/v1/logistics/requests/" + id + "/shipments",
				"{\"requestVersion\":" + version(ines, id) + ",\"kind\":\"LOCAL\"}").expectStatus(409);
		ApiClient sami = signedIn("requester.tn@demo.local");
		signedIn("requester.fr@demo.local").post("/api/v1/receipts/requests/" + id + "/service-acceptance",
				JSON.writeValueAsString(Map.of("requestVersion", version(sami, id), "comment", "Other company")))
			.expectStatus(404);
		String body = JSON.writeValueAsString(Map.of("requestVersion", version(sami, id), "comment", "Work completed"));
		sami.post("/api/v1/receipts/requests/" + id + "/service-acceptance", body).expectStatus(200);
		assertThat((String) samiStatus(id)).isEqualTo("ACCEPTED");
		sami.post("/api/v1/receipts/requests/" + id + "/service-acceptance",
				JSON.writeValueAsString(Map.of("requestVersion", version(sami, id), "comment", "Again")))
			.expectStatus(409);
		ines.post("/api/v1/receipts/requests/" + id + "/invoice", JSON.writeValueAsString(Map.of("requestVersion",
				version(ines, id), "invoiceNumber", "INV-SVC", "amount", 25, "currency", "EUR", "matchAttested", false)))
			.expectStatus(200);
		attestByManager(id);
		sami.post("/api/v1/receipts/requests/" + id + "/close", "{\"requestVersion\":" + version(sami, id) + "}")
			.expectStatus(200);
		assertThat((String) samiStatus(id)).isEqualTo("CLOSED");
	}

	@Test
	void aMixedOrderAppliesTheRulesToEachLine() {
		long id = confirmedPo("Mixed", List.of(line("Seal", "GOODS", 2), line("Fitting", "SERVICE", 1)));
		long goods = poLineId(id, 0);
		long service = poLineId(id, 1);
		ApiClient ines = signedIn("buyer@demo.local");
		ines.post("/api/v1/logistics/requests/" + id + "/shipments",
				JSON.writeValueAsString(Map.of("requestVersion", version(ines, id), "kind", "LOCAL", "lines",
						List.of(Map.of("purchaseOrderLineId", service, "quantity", 1)))))
			.expectStatus(409);
		deliver(ship(id, null));
		ApiClient omar = signedIn("receiver.tn@demo.local");
		receive(omar, id, receiptLine(service, 1, 1, 0, "OK")).expectStatus(409);
		ApiClient.Response receipt = receive(omar, id, receiptLine(goods, 2, 2, 0, "OK")).expectStatus(200);
		ApiClient sami = signedIn("requester.tn@demo.local");
		sami.post("/api/v1/receipts/goods-receipts/" + receipt.json("$.id") + "/confirm", "{}").expectStatus(200);
		assertThat((String) samiStatus(id)).isEqualTo("PARTIALLY_RECEIVED");
		List<String> missing = sami.get("/api/v1/receipts/requests/" + id).json("$.closure.missing");
		assertThat(missing).contains("service-acceptance");
		sami.post("/api/v1/receipts/requests/" + id + "/service-acceptance",
				JSON.writeValueAsString(Map.of("requestVersion", version(sami, id), "comment", "Fitted"))).expectStatus(200);
		assertThat((String) samiStatus(id)).isEqualTo("ACCEPTED");
	}

	@Test
	void anUnshippedLineOnAPartlyShippedOrderCannotBeReceived() {
		long id = confirmedPo("Partly shipped", List.of(line("Shipped", "GOODS", 4), line("Unshipped", "GOODS", 3)));
		long a = poLineId(id, 0);
		long b = poLineId(id, 1);
		deliver(ship(id, List.of(Map.of("purchaseOrderLineId", a, "quantity", 2))));
		ApiClient omar = signedIn("receiver.tn@demo.local");
		receive(omar, id, receiptLine(b, 1, 1, 0, "OK")).expectStatus(409);
		ApiClient.Response dossier = omar.get("/api/v1/receipts/requests/" + id).expectStatus(200);
		assertThat(((Number) dossier.json("$.purchaseOrder.lines[0].receivable")).doubleValue()).isEqualTo(2d);
		assertThat(((Number) dossier.json("$.purchaseOrder.lines[1].receivable")).doubleValue()).isZero();

		ApiClient.Response inTransit = ship(id, List.of(Map.of("purchaseOrderLineId", b, "quantity", 3)));
		receive(omar, id, receiptLine(b, 1, 1, 0, "OK")).expectStatus(409);
		deliver(inTransit);
		receive(omar, id, receiptLine(b, 3, 3, 0, "OK")).expectStatus(200);
	}

	@Test
	void repeatedReceiptsAreLimitedToEachShipmentLine() {
		long id = confirmedPo("Repeated receipts", List.of(line("Bearing", "GOODS", 5)));
		long poLine = poLineId(id, 0);
		deliver(ship(id, List.of(Map.of("purchaseOrderLineId", poLine, "quantity", 3))));
		deliver(ship(id, List.of(Map.of("purchaseOrderLineId", poLine, "quantity", 2))));
		ApiClient omar = signedIn("receiver.tn@demo.local");
		long first = shipmentLineId(omar, id, 0);
		long second = shipmentLineId(omar, id, 1);

		receive(omar, id, receiptLine(poLine, 1, 1, 0, "OK")).expectStatus(422);
		receive(omar, id, receiptLine(poLine, null, 0, 0, 0, "OK")).expectStatus(422);
		receive(omar, id, receiptLine(poLine, first, 4, 4, 0, "OK")).expectStatus(409);
		long otherRequest = delivered("Foreign shipment line");
		long foreign = shipmentLineId(omar, otherRequest, 0);
		receive(omar, id, receiptLine(poLine, foreign, 1, 1, 0, "OK")).expectStatus(409);

		receive(omar, id, receiptLine(poLine, first, 1, 1, 0, "OK")).expectStatus(200);
		receive(omar, id, receiptLine(poLine, first, 2, 2, 0, "OK")).expectStatus(200);
		receive(omar, id, receiptLine(poLine, first, 1, 1, 0, "OK")).expectStatus(409);
		ApiClient.Response dossier = omar.get("/api/v1/receipts/requests/" + id).expectStatus(200);
		assertThat(((Number) dossier.json("$.purchaseOrder.lines[0].shipmentLines[0].receivable")).doubleValue()).isZero();
		assertThat(((Number) dossier.json("$.purchaseOrder.lines[0].shipmentLines[1].receivable")).doubleValue()).isEqualTo(2d);
		receive(omar, id, receiptLine(poLine, 2, 2, 0, "OK")).expectStatus(200);
		assertThat(jdbc.queryForObject("""
				SELECT COUNT(*) FROM goods_receipt_line grl JOIN goods_receipt gr ON gr.id = grl.goods_receipt_id
				WHERE gr.request_id = ? AND grl.shipment_line_id = ?""", Integer.class, id, second)).isEqualTo(1);
		assertThat(jdbc.queryForObject("""
				SELECT SUM(grl.quantity_received) FROM goods_receipt_line grl JOIN goods_receipt gr ON gr.id = grl.goods_receipt_id
				WHERE gr.request_id = ?""", java.math.BigDecimal.class, id)).isEqualByComparingTo("5");
	}

	@Test
	void receiptsWithoutAShipmentLineStillCountAndAreLeftUnchanged() {
		long id = delivered("Legacy receipt");
		ApiClient omar = signedIn("receiver.tn@demo.local");
		receive(omar, id, receiptLine(poLineId(id), 1, 1, 0, "OK")).expectStatus(200);
		jdbc.update("""
				UPDATE grl SET shipment_line_id = NULL FROM goods_receipt_line grl
				JOIN goods_receipt gr ON gr.id = grl.goods_receipt_id WHERE gr.request_id = ?""", id);
		receive(omar, id, receiptLine(poLineId(id), 1, 1, 0, "OK")).expectStatus(409);
		assertThat(jdbc.queryForObject("""
				SELECT COUNT(*) FROM goods_receipt_line grl JOIN goods_receipt gr ON gr.id = grl.goods_receipt_id
				WHERE gr.request_id = ? AND grl.shipment_line_id IS NULL""", Integer.class, id)).isEqualTo(1);
	}

	@Test
	void anEarlierProblemStaysOpenAfterACleanConfirmationUntilItIsSettled() {
		long id = confirmedPo("Open problem", List.of(line("Valve", "GOODS", 3)));
		long poLine = poLineId(id, 0);
		deliver(ship(id, null));
		ApiClient omar = signedIn("receiver.tn@demo.local");
		ApiClient.Response bad = receive(omar, id, receiptLine(poLine, 1, 0, 1, "NON_CONFORMING")).expectStatus(200);
		ApiClient.Response good = receive(omar, id, receiptLine(poLine, 2, 2, 0, "OK")).expectStatus(200);
		ApiClient sami = signedIn("requester.tn@demo.local");
		sami.post("/api/v1/receipts/goods-receipts/" + bad.json("$.id") + "/confirm", "{}").expectStatus(200);
		sami.post("/api/v1/receipts/goods-receipts/" + good.json("$.id") + "/confirm", "{}").expectStatus(200);
		assertThat((String) samiStatus(id)).isEqualTo("NON_CONFORMING");
		assertThat(nonConformities(sami, id)).contains("OPEN");

		ApiClient mehdi = signedIn("purchasing.manager@demo.local");
		mehdi.post("/api/v1/receipts/requests/" + id + "/shortfall-acceptance", JSON.writeValueAsString(
				Map.of("requestVersion", version(mehdi, id), "reason", "Credit note agreed"))).expectStatus(200);
		assertThat((String) samiStatus(id)).isEqualTo("ACCEPTED");
		assertThat(nonConformities(sami, id)).contains("RESOLVED").doesNotContain("OPEN");
	}

	private List<String> nonConformities(ApiClient client, long id) {
		List<List<String>> perReceipt = client.get("/api/v1/receipts/requests/" + id).json("$.receipts[*].lines[*].nonConformity");
		return flat(perReceipt);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static List<String> flat(List values) {
		List<String> result = new ArrayList<>();
		for (Object value : values) {
			if (value instanceof List nested) {
				result.addAll(flat(nested));
			}
			else if (value != null) {
				result.add((String) value);
			}
		}
		return result;
	}

	private static int version(ApiClient client, long id) {
		return ((Number) client.get("/api/v1/requests/" + id).json("$.version")).intValue();
	}

	private ApiClient.Response ship(long id, List<Map<String, Object>> lines) {
		ApiClient ines = signedIn("buyer@demo.local");
		Map<String, Object> body = new LinkedHashMap<>(Map.of("requestVersion", version(ines, id), "kind", "LOCAL"));
		if (lines != null) {
			body.put("lines", lines);
		}
		return ines.post("/api/v1/logistics/requests/" + id + "/shipments", json(body)).expectStatus(200);
	}

	private void deliver(ApiClient.Response shipment) {
		List<Map<String, Object>> milestones = shipment.json("$.milestones");
		Object milestone = milestones.stream().filter(m -> "DELIVERED".equals(m.get("code"))).findFirst().orElseThrow().get("id");
		signedIn("buyer@demo.local").put("/api/v1/logistics/shipments/" + shipment.json("$.id") + "/milestones/" + milestone,
				"{\"actualDate\":\"" + LocalDate.now(TUNIS) + "\"}").expectStatus(200);
	}

	private static ApiClient.Response receive(ApiClient client, long id, Map<String, Object> line) {
		return client.post("/api/v1/receipts/requests/" + id + "/goods-receipts",
				json(Map.of("requestVersion", version(client, id), "lines", List.of(line))));
	}

	private static Map<String, Object> receiptLine(long poLine, Number received, Number accepted, Number rejected, String condition) {
		return receiptLine(poLine, null, received, accepted, rejected, condition);
	}

	private static Map<String, Object> receiptLine(long poLine, Long shipmentLine, Number received, Number accepted,
			Number rejected, String condition) {
		Map<String, Object> line = new LinkedHashMap<>(Map.of("purchaseOrderLineId", poLine, "quantityReceived", received,
				"quantityAccepted", accepted, "quantityRejected", rejected, "quantityDamaged", 0, "condition", condition));
		if (shipmentLine != null) {
			line.put("shipmentLineId", shipmentLine);
		}
		return line;
	}

	private static long shipmentLineId(ApiClient client, long id, int index) {
		return ((Number) client.get("/api/v1/receipts/requests/" + id)
			.json("$.purchaseOrder.lines[0].shipmentLines[" + index + "].id")).longValue();
	}

	private long poLineId(long id, int index) {
		return jdbc.queryForList("""
				SELECT pol.id FROM purchase_order_line pol JOIN purchase_order po ON po.id = pol.purchase_order_id
				WHERE po.request_id = ? ORDER BY pol.line_no""", Long.class, id).get(index);
	}

	private static Map<String, Object> line(String description, String kind, int quantity) {
		return Map.of("description", description, "quantity", quantity, "unit", "PCE", "estimatedUnitPrice", 20, "lineKind", kind);
	}

	// ---- F-5: separation of duties on receipts and invoices

	@Test
	void recordingAnInvoiceNeitherAttestsItNorAuthorisesItsPayment() {
		long id = accepted("F5 record only");
		ApiClient ines = signedIn("buyer@demo.local");
		for (String extra : new String[] { "\"matchAttested\":true", "\"paymentStatus\":\"PAID\"" }) {
			assertThat(ines.post("/api/v1/receipts/requests/" + id + "/invoice", "{\"requestVersion\":" + version(ines, id)
					+ ",\"invoiceNumber\":\"INV-F5\",\"amount\":35,\"currency\":\"EUR\"," + extra + "}").expectStatus(422)
				.body()).as(extra).contains("invoice-recorder");
		}
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM supplier_invoice WHERE request_id = ?", Integer.class, id))
			.isZero();
	}

	@Test
	void aPurchasingManagerWhoRecordsAnInvoiceNeitherAttestsNorPaysIt() {
		long id = accepted("F5 manager records");
		ApiClient mehdi = signedIn("purchasing.manager@demo.local");
		mehdi.post("/api/v1/receipts/requests/" + id + "/invoice", JSON.writeValueAsString(Map.of("requestVersion",
				version(mehdi, id), "invoiceNumber", "INV-M", "amount", 35, "currency", "EUR"))).expectStatus(200);
		assertThat(((Number) mehdi.get("/api/v1/receipts/requests/" + id).json("$.invoice.recordedByUserId")).longValue())
			.isEqualTo(userId("purchasing.manager@demo.local"));
		String body = "{\"requestVersion\":" + version(mehdi, id) + "}";
		assertThat(mehdi.post("/api/v1/receipts/requests/" + id + "/invoice/match-attested", body).expectStatus(403).code())
			.isEqualTo("invoice-recorder");
		assertThat(mehdi.post("/api/v1/receipts/requests/" + id + "/invoice/paid", body).expectStatus(403).code())
			.isEqualTo("invoice-recorder");
		// The buyer, a different person, may attest; payment still needs a purchasing manager other than the recorder.
		ApiClient ines = signedIn("buyer@demo.local");
		ines.post("/api/v1/receipts/requests/" + id + "/invoice/match-attested", body).expectStatus(200);
		assertThat(ines.post("/api/v1/receipts/requests/" + id + "/invoice/paid",
				"{\"requestVersion\":" + version(ines, id) + "}").expectStatus(403).code()).isEqualTo("payment-needs-manager");
		assertThat((String) mehdi.get("/api/v1/receipts/requests/" + id).json("$.invoice.paymentStatus"))
			.isEqualTo("PENDING");
	}

	@Test
	void theAssignedBuyerNeitherRecordsNorConfirmsTheReceipt() {
		// The buyer also holds RECEIVER for the plant: the rule is about this order, not about the role.
		ApiClient global = signedIn("global.admin@demo.local");
		long inesId = userId("buyer@demo.local");
		long tn = companyId("DEMO-TN-PLT");
		jdbc.update("INSERT INTO user_role (user_id, role, company_id) VALUES (?, 'RECEIVER', ?)", inesId, tn);
		try {
			long id = delivered("F5 buyer receives");
			assertThat(jdbc.queryForObject("SELECT assigned_buyer_user_id FROM purchase_request WHERE id = ?", Long.class,
					id)).isEqualTo(inesId);
			ApiClient ines = signedIn("buyer@demo.local");
			assertThat(ines.post("/api/v1/receipts/requests/" + id + "/goods-receipts",
					"{\"requestVersion\":" + version(ines, id) + "}").expectStatus(403).code()).isEqualTo("buyer-cannot-receive");
			// Recorded by the receiver, the buyer cannot confirm it either.
			ApiClient omar = signedIn("receiver.tn@demo.local");
			ApiClient.Response receipt = omar.post("/api/v1/receipts/requests/" + id + "/goods-receipts",
					"{\"requestVersion\":" + version(omar, id) + "}").expectStatus(200);
			assertThat(ines.post("/api/v1/receipts/goods-receipts/" + receipt.json("$.id") + "/confirm", "{}")
				.expectStatus(403)
				.code()).isEqualTo("buyer-cannot-receive");
			signedIn("requester.tn@demo.local").post("/api/v1/receipts/goods-receipts/" + receipt.json("$.id") + "/confirm",
					"{}").expectStatus(200);
		}
		finally {
			jdbc.update("DELETE FROM user_role WHERE user_id = ? AND role = 'RECEIVER' AND company_id = ?", inesId, tn);
			global.get("/api/v1/me").expectStatus(200);
		}
	}

	/** Review of F-5, findings 5 and 2: a manager who bought the order neither accepts its shortfall nor pays it. */
	@Test
	void aPurchasingManagerWhoBoughtTheOrderNeitherAcceptsItsShortfallNorPaysIt() {
		long inesId = userId("buyer@demo.local");
		long id = rejected("F5 buyer manager");
		long buying = jdbc.queryForObject("SELECT buying_company_id FROM purchase_request WHERE id = ?", Long.class, id);
		// Fixture: the assigned buyer, who issued the purchase order, is also a purchasing manager.
		jdbc.update("INSERT INTO user_role (user_id, role, company_id) VALUES (?, 'PURCHASING_MANAGER', ?)", inesId, buying);
		try {
			ApiClient ines = signedIn("buyer@demo.local");
			assertThat(ines.post("/api/v1/receipts/requests/" + id + "/shortfall-acceptance", JSON.writeValueAsString(
					Map.of("requestVersion", version(ines, id), "reason", "Credited by supplier"))).expectStatus(403).code())
				.isEqualTo("buyer-cannot-receive");
			ApiClient mehdi = signedIn("purchasing.manager@demo.local");
			mehdi.post("/api/v1/receipts/requests/" + id + "/shortfall-acceptance", JSON.writeValueAsString(
					Map.of("requestVersion", version(mehdi, id), "reason", "Credited by supplier"))).expectStatus(200);
			// Mehdi records the invoice; Ines, a manager here, would pay it: refused, she bought the order.
			mehdi.post("/api/v1/receipts/requests/" + id + "/invoice", JSON.writeValueAsString(Map.of("requestVersion",
					version(mehdi, id), "invoiceNumber", "INV-BM", "amount", 35, "currency", "EUR"))).expectStatus(200);
			assertThat(ines.post("/api/v1/receipts/requests/" + id + "/invoice/paid",
					"{\"requestVersion\":" + version(ines, id) + "}").expectStatus(403).code())
				.isEqualTo("payment-not-independent");
			assertThat((String) mehdi.get("/api/v1/receipts/requests/" + id).json("$.invoice.paymentStatus"))
				.isEqualTo("PENDING");
		}
		finally {
			jdbc.update("DELETE FROM user_role WHERE user_id = ? AND role = 'PURCHASING_MANAGER' AND company_id = ?", inesId,
					buying);
		}
	}

	/** Review of F-5, finding 1: a purchasing manager never does the buyer's work on their own request. */
	@Test
	void aRequesterWhoIsAlsoPurchasingManagerCannotSourceTheirOwnRequest() {
		long samiId = userId("requester.tn@demo.local");
		ApiClient sami = signedIn("requester.tn@demo.local");
		long id = accept(assign(approve(submit(sami, request("F5 own sourcing")))));
		long buying = jdbc.queryForObject("SELECT buying_company_id FROM purchase_request WHERE id = ?", Long.class, id);
		jdbc.update("INSERT INTO user_role (user_id, role, company_id) VALUES (?, 'PURCHASING_MANAGER', ?)", samiId, buying);
		try {
			sami = signedIn("requester.tn@demo.local");
			long skf = jdbc.queryForObject("SELECT id FROM supplier WHERE code = 'DEMO-SKF'", Long.class);
			String rfq = "{\"kind\":\"QUICK\",\"supplierIds\":[" + skf + "]}";
			assertThat(sami.post("/api/v1/sourcing/requests/" + id + "/rfq", rfq).expectStatus(403).code())
				.isEqualTo("buyer-is-requester");
			// The assigned buyer still sources it.
			signedIn("buyer@demo.local").post("/api/v1/sourcing/requests/" + id + "/rfq", rfq).expectStatus(200);
		}
		finally {
			jdbc.update("DELETE FROM user_role WHERE user_id = ? AND role = 'PURCHASING_MANAGER' AND company_id = ?", samiId,
					buying);
		}
	}

	/**
	 * CI stabilisation (deadlock seen 2026-11-18): the receipt view reads a shipment and then its DELIVERED milestone,
	 * while recording a milestone used to write the milestone and then the shipment. Concurrent, the two locked the rows
	 * in opposite order and SQL Server killed the read (503). The view must stay available while milestones change.
	 */
	@Test
	void theReceiptViewNeverDeadlocksWithAMilestoneUpdate() throws Exception {
		long id = delivered("Milestone and receipt view");
		ApiClient ines = signedIn("buyer@demo.local");
		ApiClient omar = signedIn("receiver.tn@demo.local");
		ApiClient sami = signedIn("requester.tn@demo.local");
		long shipmentId = jdbc.queryForObject("SELECT id FROM shipment WHERE request_id = ?", Long.class, id);
		long milestoneId = jdbc.queryForObject(
				"SELECT id FROM shipment_milestone WHERE shipment_id = ? AND code = 'DELIVERED'", Long.class, shipmentId);
		int milestonesBefore = jdbc.queryForObject("SELECT COUNT(*) FROM shipment_milestone WHERE shipment_id = ?",
				Integer.class, shipmentId);
		String actual = jdbc.queryForObject("SELECT CONVERT(VARCHAR(10), actual_date, 23) FROM shipment_milestone WHERE id = ?",
				String.class, milestoneId);
		int updates = 60;
		var pool = Executors.newFixedThreadPool(3);
		var done = new java.util.concurrent.atomic.AtomicBoolean(false);
		try {
			java.util.concurrent.Future<List<Integer>> writer = pool.submit(() -> {
				List<Integer> statuses = new ArrayList<>();
				try {
					for (int i = 0; i < updates; i++) {
						int version = jdbc.queryForObject("SELECT version FROM shipment WHERE id = ?", Integer.class, shipmentId);
						statuses.add(ines.put("/api/v1/logistics/shipments/" + shipmentId + "/milestones/" + milestoneId,
								"{\"version\":" + version + ",\"actualDate\":\"" + actual + "\",\"note\":\"Re-checked " + i + "\"}")
							.status());
					}
				}
				finally {
					done.set(true);
				}
				return statuses;
			});
			java.util.function.Function<ApiClient, Callable<List<Integer>>> reader = who -> () -> {
				List<Integer> statuses = new ArrayList<>();
				while (!done.get()) {
					statuses.add(who.get("/api/v1/receipts/requests/" + id).status());
				}
				return statuses;
			};
			java.util.concurrent.Future<List<Integer>> readerA = pool.submit(reader.apply(omar));
			java.util.concurrent.Future<List<Integer>> readerB = pool.submit(reader.apply(sami));
			List<Integer> written = writer.get();
			List<Integer> read = new ArrayList<>(readerA.get());
			read.addAll(readerB.get());
			assertThat(written).as("milestone updates").hasSize(updates).containsOnly(200);
			assertThat(read).as("receipt views while milestones change").isNotEmpty().containsOnly(200);
		}
		finally {
			pool.shutdownNow();
		}
		// Nothing duplicated or lost: same milestones, the delivery still recorded, no receipt created.
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shipment_milestone WHERE shipment_id = ?", Integer.class,
				shipmentId)).isEqualTo(milestonesBefore);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shipment_milestone WHERE id = ? AND actual_date IS NOT NULL",
				Integer.class, milestoneId)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM goods_receipt WHERE request_id = ?", Integer.class, id)).isZero();
		assertThat((String) samiStatus(id)).isEqualTo("DELIVERED");
		assertThat(jdbc.queryForObject("""
				SELECT COUNT(*) FROM audit_event WHERE entity_type = 'PURCHASE_REQUEST' AND entity_id = ?
				AND action = 'SHIPMENT_MILESTONE_UPDATED'""", Integer.class, Long.toString(id))).isEqualTo(updates + 1);
	}

	/** The match attested by the purchasing manager, who did not record the invoice (F-5). */
	private void attestByManager(long id) {
		ApiClient mehdi = signedIn("purchasing.manager@demo.local");
		mehdi.post("/api/v1/receipts/requests/" + id + "/invoice/match-attested",
				"{\"requestVersion\":" + version(mehdi, id) + "}").expectStatus(200);
	}

	private long confirmedPo(String title, List<Map<String, Object>> lines) {
		ApiClient sami = signedIn("requester.tn@demo.local");
		Map<String, Object> draft = request(title);
		draft.put("lines", lines);
		long id = accept(assign(approve(submit(sami, draft))));
		ApiClient ines = signedIn("buyer@demo.local");
		if (lines.stream().anyMatch(l -> "SERVICE".equals(l.get("lineKind")))) {
			classify(ines, id, List.of()).expectStatus(200);
		}
		return completeOrder(id);
	}

	private static ApiClient.Response classify(ApiClient client, long id, List<Long> asGoods) {
		return client.post("/api/v1/purchasing/requests/" + id + "/line-classification",
				json(Map.of("version", version(client, id), "reclassifyAsGoods", asGoods)));
	}

	private long completeOrder(long id) {
		ApiClient ines = signedIn("buyer@demo.local");
		long skf = jdbc.queryForObject("SELECT id FROM supplier WHERE code = 'DEMO-SKF'", Long.class);
		ApiClient.Response rfq = ines.post("/api/v1/sourcing/requests/" + id + "/rfq",
				"{\"kind\":\"QUICK\",\"supplierIds\":[" + skf + "]}").expectStatus(200);
		List<Map<String, Object>> rfqLines = rfq.json("$.lines");
		ines.post("/api/v1/sourcing/rfqs/" + rfq.json("$.id") + "/quotes", json(Map.of("rfqSupplierId", rfq.json("$.suppliers[0].id"),
				"currency", "EUR", "freightAmount", 0, "dutyAmount", 0, "otherAmount", 0, "lines",
				rfqLines.stream().map(l -> Map.of("rfqLineId", l.get("id"), "quantity", l.get("quantity"), "unitPrice", 5)).toList())))
			.expectStatus(200);
		ines.post("/api/v1/sourcing/requests/" + id + "/validate-technical",
				"{\"requestVersion\":" + version(ines, id) + ",\"comment\":\"Fits\"}").expectStatus(200);
		ines.post("/api/v1/sourcing/requests/" + id + "/validate-commercial",
				"{\"requestVersion\":" + version(ines, id) + ",\"comment\":\"Price OK\"}").expectStatus(200);
		long quoteId = ((Number) ines.get("/api/v1/sourcing/requests/" + id + "/rfq").json("$.quotes[0].id")).longValue();
		ines.post("/api/v1/ordering/requests/" + id + "/award",
				"{\"requestVersion\":" + version(ines, id) + ",\"quotationId\":" + quoteId + ",\"reason\":\"QA award\"}")
			.expectStatus(200);
		ApiClient.Response po = ines.post("/api/v1/ordering/requests/" + id + "/purchase-order",
				"{\"requestVersion\":" + version(ines, id) + "}").expectStatus(200);
		ApiClient.Response issued = ines.post("/api/v1/ordering/purchase-orders/" + po.json("$.id") + "/issue",
				"{\"version\":" + po.json("$.version") + "}").expectStatus(200);
		ines.post("/api/v1/ordering/purchase-orders/" + po.json("$.id") + "/confirm",
				"{\"version\":" + issued.json("$.version") + ",\"confirmationNote\":\"OK\"}").expectStatus(200);
		return id;
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
				"invoiceNumber", "INV-W", "amount", 35, "currency", "EUR", "matchAttested", false)))
			.expectStatus(200);
		attestByManager(id);
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

# Invoice PDF response

## Current implementation

The allocation service issues one invoice when an allocation first becomes `DELIVERED`. It stores the generated PDF, invoice number, issue date, customer ID, and total in its SQLite database. Existing allocations that were already `DELIVERED` before this feature receive an invoice on their first download. The PDF is then returned unchanged on later downloads.

The invoice number is `INV-<allocation ID>`. The issue date uses the `Pacific/Auckland` timezone. Each line total is quantity multiplied by the saved price per kg, rounded to two decimal places. The PDF now uses an A4 business invoice layout with supplier and buyer details, an item table, and a subtotal, GST, and total summary. The table rates and amounts are GST inclusive. GST is extracted from the saved total using `total × 3 ÷ 23`, rounded once to cents; the GST exclusive subtotal is `total − GST`. The payable total does not increase. This follows [Inland Revenue's method for GST inclusive prices](https://www.ird.govt.nz/gst/charging-gst). `paymentStatus` remains on the allocation and is not printed on the immutable PDF.

The PDF uses the allocation website's palette from `static/css/style.css`: teal `#17665f` for the header, table headings, GST, and total; dark text `#243333`; and the site's red danger colours for the example notice.

The sample supplier address is AUT City Campus, 55 Wellesley Street East, Auckland Central 1010, and the sample GST number is `000000000 (EXAMPLE ONLY)`. The address follows [AUT's published City Campus address](https://www.aut.ac.nz/about/campuses-and-locations/city-campus). Every newly generated PDF prominently says **EXAMPLE FORMAT - NOT TO BE USED AS BILLABLE INVOICE**, including when supplier details are configured. Set `INVOICE_SUPPLIER_NAME`, `INVOICE_SUPPLIER_ADDRESS`, and `INVOICE_SUPPLIER_GST_NUMBER` for real supplier details and remove the example notice before using invoices for actual billing. The buyer's name and phone number come from the customer service. Previously issued PDFs remain unchanged because the PDF bytes are stored when issued.

After issue, the allocation's customer, week, items, quantities, and prices cannot change. Payment and delivery statuses can still change. An allocation with an issued invoice cannot be deleted. Its PDF remains available if its delivery status later changes.

## Request and response

```http
GET /api/v1/allocations/{id}/invoice.pdf
```

For browser viewing, request `GET /api/v1/allocations/{id}/invoice.pdf?inline=true`. This returns the same saved PDF with `Content-Disposition: inline`; the default response remains an attachment download.

Successful response:

```http
HTTP/1.1 200 OK
Content-Type: application/pdf
Content-Disposition: attachment; filename="invoice-INV-42.pdf"
Cache-Control: private, no-store

<PDF bytes>
```

The allocation JSON includes `invoiceAvailable`. Both existing web allocation lists show **View Invoice PDF** and **Download Invoice PDF** controls when it is true. View opens a new browser tab. For older delivered allocations, that flag is true and the first view or download creates the invoice.

| Condition | Response |
| --- | --- |
| Allocation does not exist | `404` with `WEEKLY_ALLOCATION_NOT_FOUND` |
| Allocation is not delivered and has no invoice | `404` with `INVOICE_NOT_AVAILABLE` |
| Update changes financial details after issue | `409` with `ISSUED_INVOICE_CONFLICT` |
| Delete is requested after issue | `409` with `ISSUED_INVOICE_CONFLICT` |
| Customer or taro service is unavailable during issue | `503` with the existing service error |

The API contract is in `allocation-service/contracts/allocation-api.yaml`. The endpoint returns binary PDF bytes, not JSON or base64. Errors use the project's existing JSON error format.

## Authentication integration still required

The repository does not yet have the teammate's authentication implementation. **The PDF endpoint currently has the same open access as the other allocation endpoints.** Do not deploy it for customer use before connecting authorization.

When authentication is merged:

1. Read the authenticated principal in `InvoiceController` or an authorization service called by it. Agree on the exact role names with the teammate.
2. For a customer role, compare the principal's linked `customerId` with the invoice's stored `customerId`. Permit an administrator to download any invoice. Deny dispatchers unless requirements change. Do not trust a customer ID supplied in the URL or request body.
3. Apply the same role and ownership rules to allocation list/detail endpoints, because their JSON responses include customer and payment details. Hide links in clients as a convenience, while keeping server checks authoritative.
4. Add authentication requirements and `401`/`403` responses to the OpenAPI contract. Add tests with authenticated users for own invoice, another customer's invoice, administrator access, dispatcher denial, and no login.
5. If clients use bearer tokens, replace direct browser links with authenticated `fetch` downloads. A same-origin session cookie can continue using a normal link. Android can save the same PDF response bytes.

## Verification

Run `./mvnw test` from `allocation-service`. `InvoiceApiTest` checks availability, issue on delivery, first download for older delivered allocations, response headers, readable PDF content and GST breakdown, repeat-download immutability, payment status updates, and post-issue edit and deletion conflicts.

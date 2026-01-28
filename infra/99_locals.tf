locals {
  product = "${var.prefix}-${var.env_short}"
  project = "${var.prefix}-${var.env_short}-${var.location_short}-${var.domain}"

  apim = {
    name       = "${local.product}-apim"
    rg         = "${local.product}-api-rg"
    helpdesk_api_product_id = "technical_support_api"
  }

  receipt_pdf_datastore_hostname = var.env == "prod" ? "weu${var.env}.receipts.internal.platform.pagopa.it" : "weu${var.env}.receipts.internal.${var.env}.platform.pagopa.it"
  receipt_pdf_datastore_url      = "https://${local.receipt_pdf_datastore_hostname}/pagopa-receipt-pdf-datastore-helpdesk"

  helpdesk_api = {
    display_name          = "Receipt PDF Datastore - Helpdesk API"
    description           = "Receipt PDF Datastore API for helpdesk support"
    published             = true
    subscription_required = true
    approval_required     = false
    subscriptions_limit   = 1000
    service_url           = local.receipt_pdf_datastore_url
    path                  = "receipts/helpdesk/datastore"
  }
}

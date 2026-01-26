####################
# HELPDESK API    #
####################

## API ##

resource "azurerm_api_management_api_version_set" "api_helpdesk_api" {
  name                = format("%s-receipts-datastore-helpdesk-api", var.env_short)
  resource_group_name = local.apim.rg
  api_management_name = local.apim.name
  display_name        = local.helpdesk_api.display_name
  versioning_scheme   = "Segment"
}

# Helpdesk v1
module "apim_api_helpdesk_api_v1" {
  source = "./.terraform/modules/__v3__/api_management_api"

  name                = format("%s-receipts-datastore-helpdesk-api", var.env_short)
  api_management_name = local.apim.name
  resource_group_name = local.apim.rg
  product_ids         = [local.apim.helpdesk_api_product_id]

  subscription_required = true
  version_set_id        = azurerm_api_management_api_version_set.api_helpdesk_api.id
  api_version           = "v1"

  description  = local.helpdesk_api.description
  display_name = local.helpdesk_api.display_name
  path         = local.helpdesk_api.path
  protocols    = ["https"]
  service_url  = local.receipt_pdf_datastore_url

  content_format = "openapi"
  content_value = templatefile("../openapi/openapi.json", {
    service = local.apim.helpdesk_api_product_id
  })

  xml_content = file("./api/v1/_base_policy.xml")
}

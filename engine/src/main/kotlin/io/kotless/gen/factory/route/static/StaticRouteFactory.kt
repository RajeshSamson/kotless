package io.kotless.gen.factory.route.static

import io.kotless.HttpMethod
import io.kotless.Webapp
import io.kotless.gen.GenerationContext
import io.kotless.gen.GenerationFactory
import io.kotless.gen.factory.apigateway.RestAPIFactory
import io.kotless.gen.factory.info.InfoFactory
import io.kotless.gen.factory.resource.static.StaticResourceFactory
import io.kotless.gen.factory.route.AbstractRouteFactory
import io.kotless.hcl.HCLEntity
import io.kotless.hcl.ref
import io.kotless.terraform.provider.aws.resource.apigateway.api_gateway_integration
import io.kotless.terraform.provider.aws.resource.apigateway.api_gateway_method
import io.kotless.terraform.provider.aws.resource.apigateway.response.api_gateway_integration_response
import io.kotless.terraform.provider.aws.resource.apigateway.response.api_gateway_method_response

object StaticRouteFactory : GenerationFactory<Webapp.ApiGateway.StaticRoute, Unit>, AbstractRouteFactory() {
    override fun mayRun(entity: Webapp.ApiGateway.StaticRoute, context: GenerationContext) = context.output.check(context.webapp.api, RestAPIFactory)
        && context.output.check(context.schema.statics[entity.resource]!!, StaticResourceFactory)
        && context.output.check(context.webapp, InfoFactory)
        && context.output.check(context.webapp, StaticRoleFactory)

    override fun generate(entity: Webapp.ApiGateway.StaticRoute, context: GenerationContext): GenerationFactory.GenerationResult<Unit> {
        val api = context.output.get(context.webapp.api, RestAPIFactory)
        val resource = context.output.get(context.schema.statics[entity.resource]!!, StaticResourceFactory)
        val info = context.output.get(context.webapp, InfoFactory)
        val static_role = context.output.get(context.webapp, StaticRoleFactory)

        val resourceId = getResource(entity.path, api, context)

        val method = api_gateway_method(context.names.tf(entity.path.parts).ifBlank { "root_resource" }) {
            rest_api_id = api.rest_api_id
            resource_id = resourceId

            authorization = "NONE"
            http_method = entity.method.name
        }

        val method_response = api_gateway_method_response(context.names.tf(entity.path.parts).ifBlank { "root_resource" }) {
            rest_api_id = api.rest_api_id
            resource_id = resourceId
            http_method = method::http_method.ref
            status_code = 200
            response_parameters = object : HCLEntity() {
                val contentType by bool(name = "method.response.header.Content-Type", default = true)
                val contentLength by bool(name = "method.response.header.Content-Length", default = true)
            }
        }

        val response = api_gateway_integration_response(context.names.tf(entity.path.parts).ifBlank { "root_resource" }) {
            depends_on = arrayOf(method_response.hcl_ref)

            rest_api_id = api.rest_api_id
            resource_id = resourceId
            http_method = method::http_method.ref
            status_code = 200
            response_parameters = object : HCLEntity() {
                val contentType by text(name = "method.response.header.Content-Type", default = "integration.response.header.Content-Type")
                val contentLength by text(name = "method.response.header.Content-Length", default = "integration.response.header.Content-Length")
            }
        }

        val integration = api_gateway_integration(context.names.tf(entity.path.parts).ifBlank { "root_resource" }) {
            rest_api_id = api.rest_api_id
            resource_id = resourceId

            http_method = entity.method.name
            integration_http_method = HttpMethod.GET.name

            type = "AWS"
            uri = "arn:aws:apigateway:${info.region_name}:s3:path/${resource.bucket}/${resource.key}"
            credentials = static_role.role_arn
        }

        return GenerationFactory.GenerationResult(Unit, method, response, method_response, integration)
    }
}

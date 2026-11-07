package org.ikigaidigital.adapter.inbound.rest

import org.ikigaidigital.contract.api.TimeDepositsApi
import org.springframework.boot.webmvc.autoconfigure.WebMvcRegistrations
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.web.servlet.mvc.method.RequestMappingInfo
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import java.lang.reflect.Method

@Configuration(proxyBeanMethods = false)
class TimeDepositWebConfiguration : WebMvcRegistrations {
    override fun getRequestMappingHandlerMapping() = object : RequestMappingHandlerMapping() {
        override fun getMappingForMethod(method: Method, handlerType: Class<*>): RequestMappingInfo? {
            val mapping = super.getMappingForMethod(method, handlerType) ?: return null
            if (!TimeDepositsApi::class.java.isAssignableFrom(handlerType)) return mapping

            // The generator merges success and error content types into one Spring mapping.
            // Problem details are negotiated separately by the exception handler.
            val successTypes = mapping.producesCondition.producibleMediaTypes - MediaType.APPLICATION_PROBLEM_JSON
            return mapping.mutate().produces(*successTypes.map { it.toString() }.toTypedArray()).build()
        }
    }
}

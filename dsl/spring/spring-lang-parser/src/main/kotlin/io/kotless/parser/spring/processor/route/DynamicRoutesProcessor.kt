package io.kotless.parser.spring.processor.route

import io.kotless.Lambda
import io.kotless.ScheduledEventType
import io.kotless.Webapp.ApiGateway
import io.kotless.Webapp.Events
import io.kotless.parser.processor.AnnotationProcessor
import io.kotless.parser.processor.ProcessorContext
import io.kotless.parser.processor.config.EntrypointProcessor
import io.kotless.parser.processor.permission.PermissionsProcessor
import io.kotless.parser.utils.psi.visitNamedFunctions
import io.kotless.utils.TypedStorage
import io.kotless.utils.everyNMinutes
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import org.springframework.web.bind.annotation.RestController

internal object DynamicRoutesProcessor : AnnotationProcessor<Unit>() {
    override val annotations = setOf(RestController::class)

    override fun mayRun(context: ProcessorContext) = context.output.check(EntrypointProcessor)

    override fun process(files: Set<KtFile>, binding: BindingContext, context: ProcessorContext) {
        processClassesOrObjects(files, binding) { classOrObj, _, _ ->
            classOrObj.visitNamedFunctions(filter = { SpringAnnotationUtils.isHTTPHandler(binding, it) }) { el ->
                val entrypoint = context.output.get(EntrypointProcessor).entrypoint

                val method = SpringAnnotationUtils.getMethod(binding, el)
                val path = SpringAnnotationUtils.getRoutePath(binding, el)
                val permissions = PermissionsProcessor.process(el, binding)
                val name = el.fqName!!.asString()

                val key = TypedStorage.Key<Lambda>()
                val function = Lambda(name, context.jar, entrypoint, context.lambda, permissions)

                context.resources.register(key, function)
                context.routes.register(ApiGateway.DynamicRoute(method, path, key))

                if (context.config.optimization.autowarm.enable) {
                    context.events.register(
                        Events.Scheduled(name, everyNMinutes(context.config.optimization.autowarm.minutes), ScheduledEventType.Autowarm, key)
                    )
                }
            }
        }
    }
}

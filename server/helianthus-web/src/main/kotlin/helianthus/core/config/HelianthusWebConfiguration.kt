package helianthus.core.config

import helianthus.core.web.converter.ResultFrameCsvMessageConverter
import helianthus.core.web.converter.ResultFrameHtmlMessageConverter
import helianthus.core.web.converter.ResultFrameXmlMessageConverter
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Registers the [helianthus.core.result.ResultFrame]-specific message converters
 * (HTML, CSV, XML) ahead of the default Jackson ones so they win content-type
 * negotiation.
 */
@Configuration
class HelianthusWebConfiguration : WebMvcConfigurer {

    /**
     * Prepends the HTML/CSV/XML converters so that, for example, an
     * `Accept: text/csv` request selects the CSV converter instead of Jackson.
     */
    override fun extendMessageConverters(converters: MutableList<HttpMessageConverter<*>>) {
        converters.add(0, ResultFrameHtmlMessageConverter())
        converters.add(1, ResultFrameCsvMessageConverter())
        converters.add(2, ResultFrameXmlMessageConverter())
    }
}

package helianthus.core.config

import helianthus.core.web.converter.ResultFrameCsvMessageConverter
import helianthus.core.web.converter.ResultFrameHtmlMessageConverter
import helianthus.core.web.converter.ResultFrameJsonMessageConverter
import helianthus.core.web.converter.ResultFrameXmlMessageConverter
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class HelianthusWebConfiguration : WebMvcConfigurer {

    override fun extendMessageConverters(converters: MutableList<HttpMessageConverter<*>>) {
        converters.add(0, ResultFrameJsonMessageConverter())
        converters.add(1, ResultFrameHtmlMessageConverter())
        converters.add(2, ResultFrameCsvMessageConverter())
        converters.add(3, ResultFrameXmlMessageConverter())
    }
}

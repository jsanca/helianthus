package helianthus.core.config;

import helianthus.core.access.GenericDataAccess;
import helianthus.core.access.impl.db.ConnectionProvider;
import helianthus.core.access.impl.db.ConnectionProviderService;
import helianthus.core.access.impl.db.DataBaseGenericDataAccessImpl;
import helianthus.core.access.impl.db.DataSourcePoolConnectionProvider;
import helianthus.core.impl.OperationMappingHelianthusServiceImpl;
import helianthus.core.marshall.MarshallFormatFactory;
import helianthus.core.marshall.MarshallFormatter;
import helianthus.core.marshall.tableresult.JacksonTableResultMarshallFormatter;
import helianthus.core.marshall.tableresult.TableResultHTMLMarshallFormatter;
import helianthus.core.util.OperationMappingHelper;
import helianthus.core.util.PathHandler;
import helianthus.core.util.springframework.SpringQueryConfigurationFactoryBean;
import helianthus.core.web.workflow.WorkFlowFactory;
import helianthus.core.web.workflow.WorkFlowStep;
import helianthus.core.web.workflow.step.OperationRunnerWorkFlowStep;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
public class HelianthusLegacyConfiguration {

    @Bean(name = "mappingHelper")
    public OperationMappingHelper mappingHelper() throws Exception {
        SpringQueryConfigurationFactoryBean factoryBean =
                new SpringQueryConfigurationFactoryBean();
        factoryBean.setQueryConfigPath("queryConfig.xml");
        factoryBean.afterPropertiesSet();
        return factoryBean.getObject();
    }

    @Bean(name = "connectionProviderService")
    public ConnectionProviderService connectionProviderService(DataSource dataSource) {
        DataSourcePoolConnectionProvider provider =
                new DataSourcePoolConnectionProvider();
        provider.setDataSource(dataSource);

        Map<String, ConnectionProvider> providerMap = new HashMap<>();
        providerMap.put(GenericDataAccess.DEFAULT_DATA_SOURCE, provider);

        ConnectionProviderService service = new ConnectionProviderService();
        service.setProviderMap(providerMap);
        return service;
    }

    @Bean(name = "genericDataAccess")
    public GenericDataAccess genericDataAccess(
            ConnectionProviderService connectionProviderService) {
        DataBaseGenericDataAccessImpl dataAccess = new DataBaseGenericDataAccessImpl();
        dataAccess.setConnectionProviderService(connectionProviderService);
        return dataAccess;
    }

    @Bean(name = "helianthusService")
    public OperationMappingHelianthusServiceImpl helianthusService(
            OperationMappingHelper mappingHelper,
            GenericDataAccess genericDataAccess) {
        OperationMappingHelianthusServiceImpl service =
                new OperationMappingHelianthusServiceImpl();
        service.setMappingHelper(mappingHelper);
        service.setGenericDataAccess(genericDataAccess);
        return service;
    }

    @Bean(name = "marshallFormatFactory")
    public MarshallFormatFactory marshallFormatFactory() {
        MarshallFormatFactory factory = new MarshallFormatFactory();
        Map<String, MarshallFormatter> formatters = new HashMap<>();
        formatters.put("json", new JacksonTableResultMarshallFormatter());
        formatters.put("html", new TableResultHTMLMarshallFormatter());
        factory.setMarshallFormatterHashMap((HashMap<String, MarshallFormatter>) formatters);
        return factory;
    }

    @Bean(name = "operationRunnerWorkFlowStep")
    public WorkFlowStep operationRunnerWorkFlowStep(
            OperationMappingHelianthusServiceImpl helianthusService,
            OperationMappingHelper mappingHelper) {
        OperationRunnerWorkFlowStep step = new OperationRunnerWorkFlowStep();
        step.setHelianthusService(helianthusService);
        step.setOperationMappingHelper(mappingHelper);
        return step;
    }

    @Bean(name = "pathHandler")
    public PathHandler pathHandler() {
        return new PathHandler();
    }

    @Bean(name = "workFlowFactory")
    public WorkFlowFactory workFlowFactory(
            MarshallFormatFactory marshallFormatFactory,
            WorkFlowStep operationRunnerWorkFlowStep) {
        WorkFlowFactory factory = new WorkFlowFactory();
        factory.setMarshallFormatFactory(marshallFormatFactory);
        factory.setOperationRunnerWorkFlowStep(operationRunnerWorkFlowStep);
        return factory;
    }
}

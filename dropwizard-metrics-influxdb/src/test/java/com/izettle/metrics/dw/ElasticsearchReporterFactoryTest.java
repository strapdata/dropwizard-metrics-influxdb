package com.izettle.metrics.dw;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.net.URL;
import java.util.Map;
import java.util.Set;

import javax.validation.ConstraintViolation;
import javax.validation.Validator;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableMap;
import com.izettle.metrics.influxdb.ElasticsearchReporter;
import com.izettle.metrics.influxdb.ElasticsearchSender;

import io.dropwizard.jackson.DiscoverableSubtypeResolver;
import io.dropwizard.validation.BaseValidator;

public class ElasticsearchReporterFactoryTest {

    private InfluxDbReporterFactory factory = new InfluxDbReporterFactory();
    private final Validator validator = BaseValidator.newValidator();

    @Test
    public void isDiscoverable() throws Exception {
        assertThat(new DiscoverableSubtypeResolver().getDiscoveredSubtypes())
            .contains(InfluxDbReporterFactory.class);
    }

    @Test
    public void ensureDefaultMeasurementMappingsAreCompilable() throws Exception {
        Set<ConstraintViolation<InfluxDbReporterFactory>> violations = validator.validate(factory);
        assertThat(violations).hasSize(0);
    }

    @Test
    public void testNoAddressResolutionForElasticsearch() throws Exception {
        final ElasticsearchReporter.Builder builderSpy = mock(ElasticsearchReporter.Builder.class);
        new ElasticsearchReporterFactory() {
            @Override
            protected ElasticsearchReporter.Builder builder(MetricRegistry registry) {
                return builderSpy;
            }
        }.build(new MetricRegistry());

        final ArgumentCaptor<ElasticsearchSender> argument = ArgumentCaptor.forClass(ElasticsearchSender.class);
        verify(builderSpy).build(argument.capture());

        final ElasticsearchSender elasticsearch = argument.getValue();

        String url = new URL("http", "localhost", 9200, "/_bulk").toString();
        assertThat(getField(elasticsearch, ElasticsearchSender.class, "url")).isEqualTo(new URL(url));
        assertThat(getField(elasticsearch, ElasticsearchSender.class, "connectTimeout")).isEqualTo(1500);
        assertThat(getField(elasticsearch, ElasticsearchSender.class, "readTimeout")).isEqualTo(1500);
        assertThat(
            getField(
                elasticsearch,
                ElasticsearchSender.class,
                "authStringEncoded")).isEqualTo(Base64.encodeBase64String("".getBytes(UTF_8)));
    }

    private static Object getField(Object object, Class clazz, String name) {
        try {
            return FieldUtils.getDeclaredField(clazz, name, true).get(object);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    public void shouldReturnDefaultMeasurementMappings() {
        Map<String, String> measurementMappings = factory.buildMeasurementMappings();
        assertThat(measurementMappings).isEqualTo(factory.getDefaultMeasurementMappings());
    }

    @Test
    public void shouldChangeDefaultMappingValue() {
        ImmutableMap<String, String> mappings = ImmutableMap.of("health", "*.healthchecks.*");
        factory.setMeasurementMappings(mappings);

        Map<String, String> defaultMeasurementMappings = factory.getDefaultMeasurementMappings();
        Map<String, String> measurementMappings = factory.buildMeasurementMappings();

        assertThat(measurementMappings.size()).isEqualTo(defaultMeasurementMappings.size());
        assertThat(measurementMappings.get("health")).isEqualTo(mappings.get("health"));
    }

    @Test
    public void shouldNotChangeDefaultMappingValueWhenValueIsSame() {
        ImmutableMap<String, String> mappings = ImmutableMap.of("health", ".*\\.health(\\..*)?$");
        factory.setMeasurementMappings(mappings);

        Map<String, String> defaultMeasurementMappings = factory.getDefaultMeasurementMappings();
        Map<String, String> measurementMappings = factory.buildMeasurementMappings();

        assertThat(measurementMappings.size()).isEqualTo(defaultMeasurementMappings.size());
        assertThat(measurementMappings).isEqualTo(defaultMeasurementMappings);
    }

    @Test
    public void shouldAddNewMeasurementMapping() {
        ImmutableMap<String, String> mappingsToAdd = ImmutableMap.of("mappingKey", ".*\\.mappingValue.*");
        factory.setMeasurementMappings(mappingsToAdd);

        Map<String, String> defaultMeasurementMappings = factory.getDefaultMeasurementMappings();
        Map<String, String> measurementMappings = factory.buildMeasurementMappings();

        assertThat(measurementMappings.size()).isEqualTo(defaultMeasurementMappings.size() + mappingsToAdd.size());
        assertThat(measurementMappings).containsEntry("mappingKey", ".*\\.mappingValue.*");
    }

    @Test
    public void shouldRemoveDefaultMeasurementMappingWhenValueIsEmpty() {
        ImmutableMap<String, String> mappingsToRemove = ImmutableMap.of("health", "", "dao", "");
        factory.setMeasurementMappings(mappingsToRemove);

        Map<String, String> defaultMeasurementMappings = factory.getDefaultMeasurementMappings();
        Map<String, String> measurementMappings = factory.buildMeasurementMappings();

        assertThat(measurementMappings.size()).isEqualTo(defaultMeasurementMappings.size() - mappingsToRemove.size());
        assertThat(measurementMappings).doesNotContainKeys("health", "dao");
    }

    @Test
    public void shouldIncreaseTimeouts() throws Exception {
        final ElasticsearchReporter.Builder builderSpy = mock(ElasticsearchReporter.Builder.class);
        ElasticsearchReporterFactory factory2 = new ElasticsearchReporterFactory() {
            @Override
            protected ElasticsearchReporter.Builder builder(MetricRegistry registry) {
                return builderSpy;
            }
        };
        factory2.setConnectTimeout(2000);
        factory2.setReadTimeout(3000);
        assertThat(factory2.getConnectTimeout()).isEqualTo(2000);
        assertThat(factory2.getReadTimeout()).isEqualTo(3000);

        factory2.build(new MetricRegistry());

        final ArgumentCaptor<ElasticsearchSender> argument = ArgumentCaptor.forClass(ElasticsearchSender.class);
        verify(builderSpy).build(argument.capture());

        final ElasticsearchSender influxDb = argument.getValue();

        assertThat(getField(influxDb, ElasticsearchSender.class, "connectTimeout")).isEqualTo(2000);
        assertThat(getField(influxDb, ElasticsearchSender.class, "readTimeout")).isEqualTo(3000);
    }

}

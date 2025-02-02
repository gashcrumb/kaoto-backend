package io.kaoto.backend.api.service.step.parser.kamelet;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.kaoto.backend.api.metadata.catalog.StepCatalog;
import io.kaoto.backend.api.service.deployment.generator.kamelet.KameletBindingDeploymentGeneratorService;
import io.kaoto.backend.api.service.dsl.kamelet.KameletBindingDSLSpecification;
import io.kaoto.backend.api.service.step.parser.StepParserService;
import io.kaoto.backend.model.step.Step;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.inject.Inject;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class KameletBindingStepParserServiceTest {

    private static String twitterSearchSourceBinding;
    private static String knativeBinding;

    @Inject
    private KameletBindingDSLSpecification dslSpecification;


    private StepCatalog catalog;

    @Inject
    public void setStepCatalog(final StepCatalog catalog) {
        this.catalog = catalog;
    }

    @BeforeAll
    static void setup() throws URISyntaxException, IOException {
        twitterSearchSourceBinding = Files.readString(Path.of(
                KameletBindingStepParserServiceTest.class.getResource(
                                "twitter-search-source-binding.yaml")
                        .toURI()));
        knativeBinding = Files.readString(Path.of(
                KameletBindingStepParserServiceTest.class.getResource(
                                "knative-binding.yaml")
                        .toURI()));
    }

    @BeforeEach
    void ensureCatalog() {
        catalog.waitForWarmUp().join();
    }

    @Test
    void parse() throws JsonProcessingException {
        StepParserService.ParseResult<Step> parsed =
                dslSpecification.getStepParserService().deepParse(twitterSearchSourceBinding);
        assertThat(parsed.getSteps())
                .hasSize(3)
                .extracting(Step::getName)
                .containsExactly("twitter-search-source", "aws-translate-action", "knative");
        assertThat(parsed.getMetadata().get("name")).isEqualTo("Kamelet Binding generated by Kaoto");
        assertThat(parsed.getParameters()).isEmpty();
        var yaml = dslSpecification.getDeploymentGeneratorService()
                .parse(parsed.getSteps(), parsed.getMetadata(), parsed.getParameters());
        assertThat(yaml).isEqualToNormalizingNewlines(twitterSearchSourceBinding);
    }

    @ParameterizedTest
    @ValueSource(strings = {"dropbox-sink.kamelet.yaml", "invalid/route.yaml", "invalid/integration.yaml"})
    void deepParseInvalid(String resourcePath) throws IOException {
        String input = new String(Objects.requireNonNull(this.getClass().getResourceAsStream(resourcePath))
                .readAllBytes(), StandardCharsets.UTF_8);

        assertThatThrownBy(() -> dslSpecification.getStepParserService().deepParse(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Wrong format provided. This is not parseable by us.");
    }

    @Test
    void parseKnative() {
        StepParserService.ParseResult<Step> parsed = dslSpecification.getStepParserService().deepParse(knativeBinding);
        assertEquals(2, parsed.getSteps().size());
        var yaml = dslSpecification.getDeploymentGeneratorService()
                .parse(parsed.getSteps(), parsed.getMetadata(), parsed.getParameters());

        assertThat(yaml).isEqualToNormalizingNewlines(knativeBinding);
    }

    @Test
    void appliesTo() {
        assertTrue(dslSpecification.appliesTo(twitterSearchSourceBinding));
    }

    @ParameterizedTest
    @ValueSource(strings = {"dropbox-sink.kamelet.yaml", "invalid/route.yaml", "invalid/integration.yaml"})
    void appliesToInvalid(String resourcePath) throws IOException {
        String input = new String(Objects.requireNonNull(this.getClass().getResourceAsStream(resourcePath))
                .readAllBytes(), StandardCharsets.UTF_8);
        assertThat(dslSpecification.appliesTo(input)).isFalse();
    }

    @Test
    void parseNullSource() throws IOException {
        String input = new String(
                Objects.requireNonNull(
                        this.getClass().getResourceAsStream("null-source.binding.yaml"))
                .readAllBytes(), StandardCharsets.UTF_8);
        StepParserService.ParseResult<Step> parsed = dslSpecification.getStepParserService().deepParse(input);
        assertThat(parsed.getSteps()).hasSize(1);
        assertThat(parsed.getSteps().get(0))
                .extracting(Step::getName)
                .isEqualTo("log-sink");
        assertThat(parsed.getSteps().get(0))
                .extracting(Step::getType)
                .isEqualTo("END");
        String parsedYaml = dslSpecification.getDeploymentGeneratorService()
                .parse(parsed.getSteps(), parsed.getMetadata(),
                        parsed.getParameters());
        assertThat(parsedYaml).isEqualToNormalizingNewlines(input);
    }

    @Test
    void parseNullSink() throws IOException {
        String input = new String(
                Objects.requireNonNull(
                        this.getClass().getResourceAsStream("null-sink.binding.yaml"))
                .readAllBytes(), StandardCharsets.UTF_8);
        StepParserService.ParseResult<Step> parsed = dslSpecification.getStepParserService().deepParse(input);
        assertThat(parsed.getSteps()).hasSize(1);
        assertThat(parsed.getSteps().get(0))
                .extracting(Step::getName)
                .isEqualTo("timer-source");
        assertThat(parsed.getSteps().get(0))
                .extracting(Step::getType)
                .isEqualTo("START");
        String parsedYaml = dslSpecification.getDeploymentGeneratorService()
                .parse(parsed.getSteps(), parsed.getMetadata(),
                        parsed.getParameters());
        assertThat(parsedYaml).isEqualToNormalizingNewlines(input);
    }

    @Test
    void parseNullSourceNullSink() throws IOException {
        String input = new String(
                Objects.requireNonNull(
                        this.getClass().getResourceAsStream("null-source-null-sink.binding.yaml"))
                .readAllBytes(), StandardCharsets.UTF_8);
        StepParserService.ParseResult<Step> parsed = dslSpecification.getStepParserService().deepParse(input);
        assertThat(parsed.getSteps()).isEmpty();
        String parsedYaml = dslSpecification.getDeploymentGeneratorService()
                .parse(parsed.getSteps(), parsed.getMetadata(),
                        parsed.getParameters());
        assertThat(parsedYaml).isEqualToNormalizingNewlines(input);
    }

    @Test
    void parseNameDesc() throws IOException {
        String input = new String(
                Objects.requireNonNull(
                                this.getClass().getResourceAsStream("name-desc.binding.yaml"))
                        .readAllBytes(), StandardCharsets.UTF_8);
        List<StepParserService.ParseResult<Step>> parsed
                = dslSpecification.getStepParserService().getParsedFlows(input);
        assertThat(parsed).hasSize(2);
        assertThat(parsed.get(0).getSteps()).isNull();
        var metadata = parsed.get(0).getMetadata();
        assertThat(metadata.get("name")).isEqualTo("name-desc");
        assertThat(metadata.get("description")).isEqualTo("The name-desc KameletBinding description");
        var flowMeta = parsed.get(1).getMetadata();
        assertThat(flowMeta).containsEntry("name", "name-desc")
                .doesNotContainKey("description");
        String parsedYaml = dslSpecification.getDeploymentGeneratorService().parse(parsed);
        assertThat(parsedYaml).isEqualToNormalizingNewlines(input);
    }
}

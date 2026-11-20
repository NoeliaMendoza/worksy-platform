package com.plataformaempleo.worker.screening.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CvEvidenceTest {
    private Criterion criterion(String label) {
        return new Criterion("q1", label, "¿Cumple el criterio de la vacante?", Criterion.Kind.YES_NO, List.of("Sí", "No"));
    }

    @Test
    void conservaLaNegacionYNoModificaLaRespuestaDelModelo() {
        String text = "CV sintético. No tengo experiencia profesional con Java; solo asistí a un curso introductorio.";
        var criteria = List.of(criterion("Años de experiencia con Java"));
        var decision = new Decision(Map.of("q1", new Decision.Answer("Sí", .95)), "modelo-prueba", 1);
        var before = ScreeningOutcome.of(criteria, decision, .6);
        var after = before.withEvidence(text, criteria);

        assertThat(after.overallScore()).isEqualTo(before.overallScore());
        assertThat(after.confidence()).isEqualTo(before.confidence());
        assertThat(after.needsReview()).isEqualTo(before.needsReview());
        assertThat(after.results()).isEqualTo(before.results());
        assertThat(after.evidence().forCriterion("q1")).hasSize(1);
        assertThat(after.evidence().forCriterion("q1").getFirst().text()).isEqualTo(text);
        assertThat(after.evidence().sourceTextSha256()).matches("[0-9a-f]{64}");
        assertThat(after.evidence().toString()).doesNotContain(text);
        assertThat(after.evidence().forCriterion("q1").getFirst().toString()).doesNotContain(text);
    }

    @Test
    void javaNoCoincideConJavascriptNiSqlConPostgresql() {
        assertThat(CvEvidence.collect("Experiencia en JavaScript y PostgreSQL.", List.of(criterion("Java")))
                .forCriterion("q1")).isEmpty();
        assertThat(CvEvidence.collect("Experiencia en PostgreSQL.", List.of(criterion("SQL")))
                .forCriterion("q1")).isEmpty();
    }

    @Test
    void buscaTodosLosTerminosCercanosSinCambiarSaltosOAcentos() {
        String text = "CV 👩🏽‍💻 sintético. No creé pruebas\nAUTOMATIZADAS; estudié teoría.";
        var snippets = CvEvidence.collect(text, List.of(criterion("Pruebas automatizadas"))).forCriterion("q1");
        assertThat(snippets).hasSize(1);
        assertThat(snippets.getFirst().text()).isEqualTo(text);
        assertThat(CvEvidence.collect("Pruebas manuales" + " relleno".repeat(30) + " automatizadas",
                List.of(criterion("Pruebas automatizadas"))).forCriterion("q1")).isEmpty();
    }

    @Test
    void limitaFragmentosYConservaPosicionesExactasEnTextoLargo() {
        String text = ("Texto administrativo sin tecnología. ".repeat(20) + "Nunca trabajé con JAVA. ").repeat(8);
        var evidence = CvEvidence.collect(text, List.of(criterion("Java")));
        assertThat(evidence.forCriterion("q1")).hasSize(CvEvidence.MAX_SNIPPETS);
        for (var snippet : evidence.forCriterion("q1")) {
            assertThat(snippet.text()).isEqualTo(text.substring(snippet.startOffset(), snippet.endOffset()));
            assertThat(snippet.text().length()).isLessThanOrEqualTo(CvEvidence.MAX_SNIPPET_CHARS);
            assertThat(snippet.text()).contains("Nunca trabajé con JAVA");
        }
        assertThat(evidence).isEqualTo(CvEvidence.collect(text, List.of(criterion("Java"))));
        assertThat(evidence.sourceTextSha256()).isNotEqualTo(CvEvidence.collect(text + " cambio", List.of()).sourceTextSha256());
    }

    @Test
    void noInventaFragmentosParaEtiquetasGenericasONulas() {
        for (String label : new String[] {"Experiencia profesional", "Nivel general", "", null}) {
            assertThat(CvEvidence.collect("Amplia experiencia profesional y nivel general.", List.of(criterion(label)))
                    .forCriterion("q1")).isEmpty();
        }
    }

    @Test
    void conservaSimbolosDelLenguajeYNoConfundeCConCPlusPlus() {
        assertThat(CvEvidence.collect("Solo escribí código en C.", List.of(criterion("C++"))).forCriterion("q1")).isEmpty();
        String text = "Proyecto sintético con C++ y C#.";
        assertThat(CvEvidence.collect(text, List.of(criterion("C++"))).forCriterion("q1").getFirst().text()).isEqualTo(text);
    }
}

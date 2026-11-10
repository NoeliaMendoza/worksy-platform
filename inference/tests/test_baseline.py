from app.adapters.baseline import BaselineProvider, cosine, tokenize
from app.domain.models import DecisionState, Question, QuestionType

# CV sintéticos: no corresponden a personas reales.
CV_JAVA = "Desarrolladora backend con cinco años de experiencia en Java, Spring Boot y PostgreSQL."
CV_DISENO = "Diseñador gráfico con experiencia en ilustración, Figma y branding para agencias."
VACANTE = "Buscamos desarrollador backend Java con Spring Boot y bases de datos PostgreSQL."

PREGUNTA_JAVA = Question(
    key="cumple_java",
    type=QuestionType.CHOICE,
    instructions="¿El CV demuestra experiencia con Java?",
    criteria={"yes": "experiencia comprobable en Java Spring", "no": "sin evidencia"},
)
PREGUNTA_SCORE = Question(key="compatibilidad", type=QuestionType.SCORE,
                          instructions="Compatibilidad general con la vacante")


def test_tokenize_quita_acentos_y_palabras_vacias():
    tokens = tokenize("Diseño de APIs en Java y Python")
    assert "diseno" in tokens
    assert "java" in tokens
    assert "de" not in tokens


def test_cosine_de_textos_vacios_es_cero():
    assert cosine(tokenize(""), tokenize("java")) == 0.0


def test_choice_elige_yes_cuando_el_cv_menciona_java():
    decision = BaselineProvider().decide(DecisionState(CV_JAVA, VACANTE), [PREGUNTA_JAVA])
    answer = decision.answers["cumple_java"]
    assert answer.choice == "yes"
    assert 0.5 < answer.probability <= 1.0


def test_choice_elige_no_cuando_el_cv_no_menciona_java():
    decision = BaselineProvider().decide(DecisionState(CV_DISENO, VACANTE), [PREGUNTA_JAVA])
    assert decision.answers["cumple_java"].choice == "no"


def test_choice_con_tres_opciones_usa_similitud():
    pregunta = Question(
        key="nivel",
        type=QuestionType.CHOICE,
        instructions="Área principal del perfil",
        criteria={"backend": "java spring postgresql", "diseno": "figma ilustracion branding",
                  "datos": "python pandas estadistica"},
    )
    decision = BaselineProvider().decide(DecisionState(CV_DISENO, VACANTE), [pregunta])
    assert decision.answers["nivel"].choice == "diseno"


def test_score_es_mayor_para_el_cv_compatible():
    provider = BaselineProvider()
    compatible = provider.decide(DecisionState(CV_JAVA, VACANTE), [PREGUNTA_SCORE]).answers["compatibilidad"]
    ajeno = provider.decide(DecisionState(CV_DISENO, VACANTE), [PREGUNTA_SCORE]).answers["compatibilidad"]
    assert 0.0 <= ajeno.score < compatible.score <= 1.0


def test_es_determinista():
    provider = BaselineProvider()
    a = provider.decide(DecisionState(CV_JAVA, VACANTE), [PREGUNTA_JAVA, PREGUNTA_SCORE])
    b = provider.decide(DecisionState(CV_JAVA, VACANTE), [PREGUNTA_JAVA, PREGUNTA_SCORE])
    assert a == b

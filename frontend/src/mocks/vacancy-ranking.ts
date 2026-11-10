import type { CriterionAnswer, RankedCandidate, VacancyRanking } from '@/features/ranking/types'

/**
 * Datos SINTÉTICOS para la pantalla de referencia del reclutador.
 * Ninguna persona, empresa ni CV es real. Se reemplaza por la API en F1/F2.
 */

const criteria = [
  { id: 'spring', label: 'Spring Boot', question: '¿Tiene experiencia profesional con Spring Boot?' },
  { id: 'colas', label: 'Mensajería', question: '¿Ha trabajado con colas de mensajes (RabbitMQ o Kafka)?' },
  { id: 'anios', label: 'Años en Java', question: '¿Cuántos años de experiencia tiene con Java?' },
  { id: 'sql', label: 'PostgreSQL', question: '¿Ha diseñado esquemas o consultas en PostgreSQL?' },
  { id: 'ingles', label: 'Inglés B2', question: '¿Tiene un nivel de inglés B2 o superior?' },
]

type A = [answer: string, outcome: CriterionAnswer['outcome'], evidence: string]

function answers(...items: A[]): CriterionAnswer[] {
  return items.map(([answer, outcome, evidence], i) => ({
    criterionId: criteria[i]!.id,
    answer,
    outcome,
    evidence,
  }))
}

const candidates: RankedCandidate[] = [
  {
    id: 'c-101',
    rank: 1,
    fullName: 'Valeria Quispe Romero',
    headline: 'Desarrolladora backend en una fintech, 6 años',
    location: 'Arequipa',
    receivedLabel: 'Recibido hace 2 días',
    score: 91,
    confidence: 'alta',
    decision: 'preseleccionado',
    answers: answers(
      ['Sí', 'cumple', 'Migró 14 microservicios de Spring Boot 2.7 a 3.2 sin tiempo de inactividad.'],
      ['Sí', 'cumple', 'Diseñó la cola de conciliación de pagos con RabbitMQ y reintentos con espera exponencial.'],
      ['Más de 5 años', 'cumple', 'Java desde 2019 en dos empresas de servicios financieros.'],
      ['Sí', 'cumple', 'Particionó tablas de movimientos en PostgreSQL 15 para consultas por mes.'],
      ['B2', 'cumple', 'Certificado B2 First (2022).'],
    ),
  },
  {
    id: 'c-102',
    rank: 2,
    fullName: 'Andrés Villanueva Soto',
    headline: 'Ingeniero de software en logística, 5 años',
    location: 'Lima',
    receivedLabel: 'Recibido hace 3 días',
    score: 87,
    confidence: 'alta',
    decision: null,
    answers: answers(
      ['Sí', 'cumple', 'Mantiene la API de seguimiento de envíos construida con Spring Boot 3.'],
      ['Sí', 'cumple', 'Publica eventos de entrega en Kafka para el panel de operaciones.'],
      ['3 a 5 años', 'cumple', 'Java como lenguaje principal desde 2020.'],
      ['Sí', 'cumple', 'Optimizó índices en PostgreSQL y bajó el tiempo de una consulta de 4 s a 180 ms.'],
      ['B1', 'parcial', 'Indica "inglés intermedio" sin certificado.'],
    ),
  },
  {
    id: 'c-103',
    rank: 3,
    fullName: 'Camila Herrera Paz',
    headline: 'Desarrolladora Java en consultora, 4 años',
    location: 'Trujillo',
    receivedLabel: 'Recibido hace 1 día',
    score: 82,
    confidence: 'media',
    decision: null,
    answers: answers(
      ['Sí', 'cumple', 'Proyectos de clientes en Spring Boot con Spring Security y OAuth 2.'],
      ['Parcial', 'parcial', 'Menciona "integraciones asíncronas" sin nombrar la tecnología.'],
      ['3 a 5 años', 'cumple', 'Cuatro años en proyectos Java para banca y seguros.'],
      ['Sí', 'cumple', 'Escribió migraciones con Flyway sobre PostgreSQL.'],
      ['C1', 'cumple', 'Trabajó con un equipo distribuido en Canadá durante dos años.'],
    ),
  },
  {
    id: 'c-104',
    rank: 4,
    fullName: 'Diego Paredes Luna',
    headline: 'Backend developer en comercio electrónico, 7 años',
    location: 'Remoto, Cusco',
    receivedLabel: 'Recibido hace 5 días',
    score: 79,
    confidence: 'alta',
    decision: 'en_espera',
    answers: answers(
      ['Sí', 'cumple', 'Lideró el servicio de catálogo con Spring Boot y Spring Data JPA.'],
      ['Sí', 'cumple', 'Usó RabbitMQ para el inventario entre almacenes.'],
      ['Más de 5 años', 'cumple', 'Siete años con Java, desde Java 8 hasta Java 21.'],
      ['No', 'no_cumple', 'Su experiencia en bases de datos es con MySQL y MongoDB.'],
      ['B2', 'cumple', 'Documentación técnica en inglés para un cliente de EE. UU.'],
    ),
  },
  {
    id: 'c-105',
    rank: 5,
    fullName: 'Lucía Fernández Ortiz',
    headline: 'Desarrolladora full stack en salud digital, 3 años',
    location: 'Lima',
    receivedLabel: 'Recibido hace 4 horas',
    score: 74,
    confidence: 'media',
    decision: null,
    answers: answers(
      ['Sí', 'cumple', 'Desarrolló el módulo de citas con Spring Boot y React.'],
      ['No', 'no_cumple', ''],
      ['3 a 5 años', 'parcial', 'Tres años, uno de ellos en prácticas profesionales.'],
      ['Sí', 'cumple', 'Modeló historias clínicas en PostgreSQL con columnas JSONB.'],
      ['B2', 'cumple', 'Certificado IELTS 6.5.'],
    ),
  },
  {
    id: 'c-106',
    rank: 6,
    fullName: 'Mateo Rojas Aguilar',
    headline: 'Ingeniero de integración en telecomunicaciones, 8 años',
    location: 'Chiclayo',
    receivedLabel: 'Recibido hace 6 días',
    score: 68,
    confidence: 'media',
    decision: null,
    answers: answers(
      ['Parcial', 'parcial', 'Trabaja con Spring Framework clásico; Spring Boot solo en un proyecto interno.'],
      ['Sí', 'cumple', 'Administra colas IBM MQ y migró parte del tráfico a RabbitMQ.'],
      ['Más de 5 años', 'cumple', 'Ocho años con Java en integraciones SOAP y REST.'],
      ['Parcial', 'parcial', 'Consultas en Oracle; menciona PostgreSQL en un curso.'],
      ['B1', 'parcial', 'Inglés técnico de lectura.'],
    ),
  },
  {
    id: 'c-107',
    rank: 7,
    fullName: 'Sofía Mendoza Ríos',
    headline: 'Desarrolladora backend junior, 2 años',
    location: 'Piura',
    receivedLabel: 'Recibido hace 2 días',
    score: 61,
    confidence: 'baja',
    decision: null,
    answers: answers(
      ['Sí', 'cumple', 'API REST con Spring Boot en su empleo actual.'],
      ['No', 'no_cumple', ''],
      ['1 a 2 años', 'no_cumple', 'Dos años de experiencia profesional.'],
      ['Sí', 'parcial', 'Menciona PostgreSQL en la lista de herramientas, sin proyectos.'],
      ['Sin dato', 'parcial', ''],
    ),
  },
  {
    id: 'c-108',
    rank: 8,
    fullName: 'Gabriel Torres Núñez',
    headline: 'Desarrollador .NET con transición a Java, 6 años',
    location: 'Huancayo',
    receivedLabel: 'Recibido hace 1 semana',
    score: 52,
    confidence: 'baja',
    decision: 'descartado',
    answers: answers(
      ['Parcial', 'parcial', 'Curso de Spring Boot completado en 2026; sin experiencia laboral.'],
      ['Sí', 'cumple', 'Usó Azure Service Bus en proyectos .NET.'],
      ['Menos de 1 año', 'no_cumple', 'Seis años con C#; Java solo en proyectos personales.'],
      ['Sí', 'cumple', 'PostgreSQL en dos sistemas de facturación.'],
      ['B2', 'cumple', 'Certificado B2 (2021).'],
    ),
  },
]

export const vacancyRankingMock: VacancyRanking = {
  vacancy: {
    id: 'v-2041',
    title: { lead: 'Desarrollador/a backend', emphasis: 'Java' },
    company: 'Andina Cargo (empresa ficticia)',
    location: 'Lima',
    modality: 'Híbrido',
    publishedLabel: 'Publicada el 28 de octubre',
    criteria,
    modelVersion: 'baseline-bow-1',
    analyzedLabel: 'Analizado hoy a las 09:40',
  },
  candidates,
}

export type RankingPreview = 'listo' | 'cargando' | 'vacio' | 'error'

/**
 * Simula la llamada a la API. `preview` permite revisar los estados de la pantalla
 * desde la URL (?estado=cargando|vacio|error) sin backend.
 */
export function fetchVacancyRanking(preview: RankingPreview, delayMs = 500): Promise<VacancyRanking> {
  return new Promise((resolve, reject) => {
    if (preview === 'cargando') return // se queda cargando a propósito
    setTimeout(() => {
      if (preview === 'error') reject(new Error('El servicio de preselección no respondió (tiempo de espera agotado).'))
      else if (preview === 'vacio') resolve({ ...vacancyRankingMock, candidates: [] })
      else resolve(structuredClone(vacancyRankingMock))
    }, delayMs)
  })
}

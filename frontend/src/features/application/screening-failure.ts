const messages: Record<string, string> = {
  PDF_NO_TEXT: 'El PDF no contiene texto que podamos analizar. Revisa el CV manualmente; reintentar el mismo archivo no resolverá el problema.',
  CV_TEXT_TOO_LONG: 'El CV supera el límite de texto para el análisis. Revisa el documento completo manualmente.',
  PDF_PASSWORD_PROTECTED: 'El PDF está protegido con contraseña. Solicita una copia sin protección para poder analizarlo.',
  PDF_UNREADABLE: 'No pudimos leer el PDF. Revisa el documento manualmente o solicita una copia legible.',
  CV_NOT_FOUND: 'El archivo del CV no está disponible. Contacta con soporte para recuperar el documento.',
  STORAGE_UNAVAILABLE: 'No pudimos acceder al archivo por un problema temporal. Puedes reintentar el análisis más tarde.',
  INFERENCE_UNAVAILABLE: 'El servicio de análisis no está disponible temporalmente. Puedes reintentarlo más tarde o revisar el CV manualmente.',
  INFERENCE_NOT_CONFIGURED: 'El servicio de análisis necesita configuración. Contacta con soporte; mientras tanto, revisa el CV manualmente.',
  INFERENCE_REJECTED: 'El servicio no pudo analizar este documento con los criterios enviados. Revisa el CV manualmente o contacta con soporte.',
  INFERENCE_INVALID_RESPONSE: 'El servicio devolvió un resultado que no se pudo validar. Revisa el CV manualmente o contacta con soporte.',
}

/** Solo mensajes conocidos: nunca mostramos detalles internos recibidos como código. */
export function screeningFailureMessage(code: string | null | undefined): string {
  const message = code && Object.hasOwn(messages, code) ? messages[code] : undefined
  return message ?? 'No se pudo completar el análisis y no hay un motivo específico disponible. Abre el CV para revisarlo manualmente.'
}

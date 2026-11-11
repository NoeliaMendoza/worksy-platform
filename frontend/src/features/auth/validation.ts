/** Validación de formato en el cliente. La regla definitiva la aplica la API. */
export const EMAIL_PATTERN = /^[^@\s]+@[^@\s]+\.[^@\s]+$/

/** Debe coincidir con PasswordPolicy en la API. */
export const PASSWORD_MIN = 10
export const PASSWORD_MAX = 128

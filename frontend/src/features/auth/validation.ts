/** Validación de formato en el cliente. La regla definitiva la aplica la API. */
export const EMAIL_PATTERN = /^[^@\s]+@[^@\s]+\.[^@\s]+$/

/** Debe coincidir con PasswordPolicy en la API. */
export const PASSWORD_MIN = 10
/** BCrypt solo admite 72 bytes: la API limita a 64 caracteres y 72 bytes. */
export const PASSWORD_MAX = 64

import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'

// Las pruebas no abren WebSocket reales: el tiempo real se simula en las pruebas que lo necesitan.
vi.mock('@/lib/realtime-client', () => ({ openRealtime: vi.fn(() => () => undefined) }))

afterEach(() => cleanup())

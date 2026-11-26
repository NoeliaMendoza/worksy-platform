import { useCallback, useEffect, useRef, useState } from 'react'

export type MediaProblem = 'denied' | 'missing' | 'busy' | 'unsupported' | null
export type Devices = { microphones: MediaDeviceInfo[]; cameras: MediaDeviceInfo[]; speakers: MediaDeviceInfo[] }

/** Mensaje comprensible para cada problema con la cámara o el micrófono. */
export const MEDIA_PROBLEM: Record<Exclude<MediaProblem, null>, string> = {
  denied: 'El navegador no tiene permiso para usar tu cámara y tu micrófono. Permítelo desde el candado de la barra de direcciones y vuelve a intentarlo.',
  missing: 'No encontramos cámara ni micrófono. Conecta uno y vuelve a intentarlo; también puedes entrar solo para escuchar.',
  busy: 'Otra aplicación está usando la cámara o el micrófono. Ciérrala y vuelve a intentarlo.',
  unsupported: 'Este navegador no permite videollamadas. Usa una versión reciente de Chrome, Edge, Firefox o Safari.',
}

function problemOf(error: unknown): MediaProblem {
  const name = error instanceof DOMException ? error.name : ''
  if (name === 'NotAllowedError' || name === 'SecurityError') return 'denied'
  if (name === 'NotFoundError' || name === 'OverconstrainedError') return 'missing'
  if (name === 'NotReadableError' || name === 'AbortError') return 'busy'
  return 'missing'
}

/**
 * Cámara y micrófono propios: permisos, elección de dispositivo, encendido y apagado, y avisos si un dispositivo
 * se desconecta. Al desmontar, apaga todo (la luz de la cámara se apaga al salir).
 */
export function useLocalMedia() {
  const [stream, setStream] = useState<MediaStream | null>(null)
  const [problem, setProblem] = useState<MediaProblem>(null)
  const [devices, setDevices] = useState<Devices>({ microphones: [], cameras: [], speakers: [] })
  const [micId, setMicId] = useState('')
  const [camId, setCamId] = useState('')
  const [micOn, setMicOn] = useState(true)
  const [camOn, setCamOn] = useState(true)
  const [lost, setLost] = useState<string | null>(null)
  const current = useRef<MediaStream | null>(null)
  const mounted = useRef(false)

  const refreshDevices = useCallback(async () => {
    const all = await navigator.mediaDevices?.enumerateDevices?.() ?? []
    setDevices({
      microphones: all.filter((d) => d.kind === 'audioinput'),
      cameras: all.filter((d) => d.kind === 'videoinput'),
      speakers: all.filter((d) => d.kind === 'audiooutput'),
    })
  }, [])

  const acquire = useCallback(async (mic = micId, cam = camId) => {
    if (!navigator.mediaDevices?.getUserMedia) {
      setProblem('unsupported')
      return null
    }
    let next: MediaStream
    try {
      next = await navigator.mediaDevices.getUserMedia({
        audio: mic ? { deviceId: { exact: mic } } : true,
        video: cam ? { deviceId: { exact: cam } } : { width: { ideal: 1280 }, height: { ideal: 720 } },
      })
    } catch (error) {
      // Sin cámara se intenta solo con el micrófono.
      try {
        next = await navigator.mediaDevices.getUserMedia({ audio: mic ? { deviceId: { exact: mic } } : true })
      } catch {
        setProblem(problemOf(error))
        return null
      }
    }
    if (!mounted.current) {
      next.getTracks().forEach((t) => t.stop())
      return null
    }
    current.current?.getTracks().forEach((t) => t.stop())
    next.getAudioTracks().forEach((t) => { t.enabled = micOn })
    next.getVideoTracks().forEach((t) => { t.enabled = camOn })
    next.getTracks().forEach((track) => {
      track.addEventListener('ended', () => setLost(track.kind === 'video'
        ? 'Tu cámara se desconectó. Elige otra o vuelve a conectarla.'
        : 'Tu micrófono se desconectó. Elige otro o vuelve a conectarlo.'))
    })
    current.current = next
    setStream(next)
    setProblem(null)
    setLost(null)
    await refreshDevices()
    return next
    // micOn y camOn se aplican al obtener la pista; no deben volver a pedir permisos.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [micId, camId, refreshDevices])

  useEffect(() => {
    mounted.current = true
    // Se pide la cámara después de montar; si la pantalla se cierra antes, la pista se apaga al llegar.
    void Promise.resolve().then(() => acquire())
    const media = navigator.mediaDevices
    const onChange = () => void refreshDevices()
    media?.addEventListener?.('devicechange', onChange)
    return () => {
      media?.removeEventListener?.('devicechange', onChange)
      mounted.current = false
      current.current?.getTracks().forEach((t) => t.stop())
      current.current = null
    }
    // Solo al montar: los cambios de dispositivo llaman a acquire de forma explícita.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const toggleMic = useCallback(() => setMicOn((on) => {
    current.current?.getAudioTracks().forEach((t) => { t.enabled = !on })
    return !on
  }), [])
  const toggleCam = useCallback(() => setCamOn((on) => {
    current.current?.getVideoTracks().forEach((t) => { t.enabled = !on })
    return !on
  }), [])

  const chooseMic = useCallback(async (id: string) => { setMicId(id); return acquire(id, camId) }, [acquire, camId])
  const chooseCam = useCallback(async (id: string) => { setCamId(id); return acquire(micId, id) }, [acquire, micId])

  const stop = useCallback(() => {
    current.current?.getTracks().forEach((t) => t.stop())
    current.current = null
    setStream(null)
  }, [])

  return { stream, problem, devices, micId, camId, micOn, camOn, lost, retry: () => acquire(), chooseMic, chooseCam, toggleMic, toggleCam, stop }
}

import { Check, Copy, Eye, EyeOff } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useToast } from './Toast'

const AUTO_HIDE_MS = 30_000

interface Props {
  secret: string
  /** Notas seguras são texto longo: exibidas em bloco com quebras de linha. */
  multiline?: boolean
}

/**
 * Exibe o segredo mascarado por padrão. O valor fica apenas em memória (props/estado do React):
 * nada é gravado em localStorage/sessionStorage. Ao revelar, oculta de novo automaticamente após 30 s.
 * O conteúdo é renderizado como texto (React escapa), nunca como HTML.
 */
export function SecretField({ secret, multiline = false }: Props) {
  const [visible, setVisible] = useState(false)
  const [copied, setCopied] = useState(false)
  const toast = useToast()
  const hideTimer = useRef<number>()
  const copyTimer = useRef<number>()

  useEffect(
    () => () => {
      window.clearTimeout(hideTimer.current)
      window.clearTimeout(copyTimer.current)
    },
    [],
  )

  function toggle() {
    window.clearTimeout(hideTimer.current)
    if (!visible) {
      hideTimer.current = window.setTimeout(() => setVisible(false), AUTO_HIDE_MS)
    }
    setVisible(!visible)
  }

  async function copy() {
    try {
      await navigator.clipboard.writeText(secret)
      setCopied(true)
      window.clearTimeout(copyTimer.current)
      copyTimer.current = window.setTimeout(() => setCopied(false), 2000)
      toast.success('Segredo copiado para a área de transferência.')
    } catch {
      toast.error('Não foi possível copiar. Seu navegador pode ter bloqueado o acesso à área de transferência.')
    }
  }

  const masked = '•'.repeat(Math.min(Math.max(secret.length, 8), 24))

  return (
    <div className="flex items-start gap-2">
      <div
        className={`min-w-0 flex-1 rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 font-mono text-sm ${
          multiline && visible ? 'whitespace-pre-wrap break-words' : 'truncate'
        }`}
        data-testid="secret-value"
      >
        {visible ? secret : masked}
      </div>
      <button
        type="button"
        className="btn btn-secondary px-3"
        onClick={toggle}
        aria-pressed={visible}
        aria-label={visible ? 'Ocultar segredo' : 'Mostrar segredo'}
        title={visible ? 'Ocultar' : 'Mostrar'}
      >
        {visible ? <EyeOff className="h-4 w-4" aria-hidden /> : <Eye className="h-4 w-4" aria-hidden />}
      </button>
      <button type="button" className="btn btn-secondary px-3" onClick={copy} aria-label="Copiar segredo" title="Copiar">
        {copied ? <Check className="h-4 w-4 text-emerald-400" aria-hidden /> : <Copy className="h-4 w-4" aria-hidden />}
      </button>
    </div>
  )
}

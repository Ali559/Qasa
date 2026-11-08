// Clickjacking checks (plan §8.3). A page can try to make the user fill a field they can't
// see (transparent or covered), or to hide or restyle our own menu so a click on a decoy
// lands on it. Before any fill we check both the target field and, for menu clicks, the
// menu itself. Every check fails closed.

/** Product of `opacity` over the element and its ancestors (crossing shadow hosts and,
 *  for same-origin frames, the frame element). Also fails on hidden/filtered ancestors. */
export function effectiveOpacity(el: Element): number {
  let product = 1
  for (let node: Element | null = el; node; node = composedParent(node)) {
    const style = node.ownerDocument.defaultView?.getComputedStyle(node)
    if (!style) return 0
    if (style.display === 'none' || style.visibility === 'hidden' || style.visibility === 'collapse') return 0
    if (style.filter && style.filter !== 'none' && /opacity\(|blur\(|brightness\(0/.test(style.filter)) return 0
    product *= Number.parseFloat(style.opacity || '1')
  }
  return product
}

function composedParent(el: Element): Element | null {
  if (el.parentElement) return el.parentElement
  const root = el.getRootNode() as ShadowRoot | Document
  if ('host' in root && root.host) return root.host
  // Top of a same-origin child frame: continue in the parent document.
  try {
    return (root as Document).defaultView?.frameElement ?? null
  } catch {
    return null
  }
}

/** The deepest element at a point, descending into open shadow roots. */
function deepElementFromPoint(doc: Document, x: number, y: number): Element | null {
  let hit = doc.elementFromPoint(x, y)
  while (hit?.shadowRoot) {
    const inner = hit.shadowRoot.elementFromPoint(x, y)
    if (!inner || inner === hit) break
    hit = inner
  }
  return hit
}

/**
 * The field is really on screen: non-zero size, its centre inside the viewport, the
 * topmost element at its centre is the field itself (or its label, or our own icon
 * host), and it and its ancestors are not mostly transparent or hidden.
 * `ownHost` is our overlay host in the field's document, which may sit on top of a small field.
 */
export function isFieldVisible(field: HTMLInputElement, ownHost?: Element | null): boolean {
  const doc = field.ownerDocument
  const win = doc.defaultView
  if (!win) return false
  const rect = field.getBoundingClientRect()
  if (rect.width < 2 || rect.height < 2) return false
  const cx = rect.left + rect.width / 2
  const cy = rect.top + rect.height / 2
  if (cx < 0 || cy < 0 || cx > win.innerWidth || cy > win.innerHeight) return false
  if (effectiveOpacity(field) < 0.5) return false
  const hit = deepElementFromPoint(doc, cx, cy)
  if (!hit) return false
  const ok =
    hit === field ||
    field.contains(hit) ||
    (ownHost != null && hit === ownHost) ||
    (hit.tagName === 'LABEL' && (hit as HTMLLabelElement).control === field)
  if (!ok) return false
  // A field inside a same-origin child frame: the frame must be visible in its parent too.
  const frame = win.frameElement as HTMLIFrameElement | null
  if (frame && !isFrameVisible(frame)) return false
  return true
}

function isFrameVisible(frame: HTMLIFrameElement): boolean {
  const rect = frame.getBoundingClientRect()
  if (rect.width < 2 || rect.height < 2 || effectiveOpacity(frame) < 0.5) return false
  const cx = rect.left + rect.width / 2
  const cy = rect.top + rect.height / 2
  return deepElementFromPoint(frame.ownerDocument, cx, cy) === frame
}

/** Inline style on our host. Inline `!important` beats any page stylesheet; a page script
 *  that edits the attribute is caught by `isHostIntact`. */
export const HOST_STYLE = [
  'all: initial',
  'position: fixed',
  'top: 0',
  'left: 0',
  'width: 0',
  'height: 0',
  'margin: 0',
  'padding: 0',
  'border: 0',
  'display: block',
  'opacity: 1',
  'visibility: visible',
  'filter: none',
  'transform: none',
  'clip-path: none',
  'mix-blend-mode: normal',
  'overflow: visible',
  'pointer-events: none',
  'z-index: 2147483647',
]
  .map((d) => `${d} !important`)
  .join('; ')

/**
 * Our menu host was not tampered with and the click really landed on it:
 *   - its style attribute is still ours, and its computed style matches it;
 *   - it and <html> are fully opaque, unfiltered and visible;
 *   - the topmost element at the click point is the host (nothing on top that takes clicks);
 *   - where the browser supports IntersectionObserver v2, the menu was last reported visible
 *     (this also catches covers with `pointer-events: none`, which hit-testing skips).
 */
export function isHostIntact(host: HTMLElement, click: { x: number; y: number }, ioVisible: boolean | null): boolean {
  if (host.getAttribute('style') !== HOST_STYLE) return false
  if (host.parentNode !== host.ownerDocument.documentElement) return false
  const win = host.ownerDocument.defaultView
  if (!win) return false
  const style = win.getComputedStyle(host)
  if (
    style.opacity !== '1' ||
    style.visibility !== 'visible' ||
    style.display !== 'block' ||
    style.filter !== 'none' ||
    style.transform !== 'none' ||
    style.position !== 'fixed'
  ) {
    return false
  }
  if (effectiveOpacity(host) < 0.99) return false
  if (host.ownerDocument.elementFromPoint(click.x, click.y) !== host) return false
  if (ioVisible === false) return false
  return true
}

/** Whether the browser can report occlusion (Chrome's IntersectionObserver v2). */
export function supportsOcclusion(): boolean {
  return typeof IntersectionObserverEntry !== 'undefined' && 'isVisible' in IntersectionObserverEntry.prototype
}

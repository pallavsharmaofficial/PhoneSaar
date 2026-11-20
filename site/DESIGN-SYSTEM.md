# PhoneSaar site design system

Shared by `index.html`, `privacy.html` and (later) `launcher.html`. Link `styles.css`, add `<script>document.documentElement.classList.add('js');</script>` in the head, and load `js/site.js` with `defer`.

## Tokens (CSS variables on `:root`)

| Group | Variables |
|---|---|
| Surface | `--bg`, `--bg-2`, `--surface`, `--surface-glass`, `--line` |
| Text | `--text`, `--muted`, `--link`, `--on-night`, `--on-night-muted` |
| Brand | `--brand` (violet), `--brand-2`, `--teal`, `--teal-bright`, `--accent` (warm amber), `--accent-ink` |
| Gradients | `--grad-brand`, `--grad-night` (hero, safety, download card), `--grad-cta` (primary button) |
| Status | `--good`, `--good-bg`, `--warn`, `--warn-bg` |
| Shape | `--radius`, `--radius-sm`, `--shadow-1`, `--shadow-2`, `--max`, `--gutter`, `--sec`, `--ease`, `--font` |

Dark mode swaps the surface, text and shadow tokens through `prefers-color-scheme`. Add `data-theme="light"` on `<html>` to pin light. The nav, hero and `.on-night` sections stay dark in both modes.

## Components

- Layout: `.wrap` (centred container), `.section`, `.section.alt`, `.section.on-night`, `.section-head` (`.center`), `.eyebrow`, `.lead`, `.fine`, `.grad-text`, `.skip`.
- Nav: `header.nav > .wrap > a.brand + nav.nav-links` (`a.hide-m` hides on small screens, `a.keep` stays), `.lang-toggle` with `button[data-lang]`.
- Buttons: `.btn`, `.btn-primary` (amber gradient), `.btn-ghost` (for dark backgrounds), `.btn-lg`, `.cta` (button row).
- Hero: `.hero` > `.mesh` (three animated gradient blobs) + `.wrap.hero-grid` > text + `.hero-stage`; `.glow`, `.chips > .chip`, `.meter` (before/after ring), `.float-card`.
- Device frame: `.device-wrap > .device (--w: <px>) > .screen > img`; size helpers `.dw-sm/.dw-md/.dw-lg`, `.still` stops the float animation. Put any markup inside `.screen` for a CSS-built mock.
- Cards: `.card` (glass), `.glass-dark`, `.icon-badge`, `.promise-grid`, `.steps-strip`.
- Feature row: `.feature` (`.flip` swaps sides) > `.feature-text` + `.feature-visual` (`.duo` for two phones); `.pill`.
- Steps: `ol.steps` (numbered with a connecting line), `.tick-list`.
- Note: `.note` (amber callout, `h3` + `p`).
- Accordion: `.acc > details > summary + .acc-body` (native, keyboard accessible).
- Teaser/download: `.teaser`, `.dl-card`, `.dl-meta`, `.download`, `.two-col`.
- Page head for plain pages: `.page-head` + `main.narrow`.
- Footer: `footer > .wrap > div + ul`.

## Motion

- Add `.reveal` (optionally `.from-l` / `.from-r`, `style="--d:.1s"` for delay) to animate in on scroll; `js/site.js` adds `.in`.
- Everything animated is switched off under `prefers-reduced-motion`.

## Language

Add `data-hi="Hindi copy"` to any element whose inner HTML has a Hindi version; the English markup is kept as the default and `js/site.js` swaps it. The choice is stored in `localStorage` under `phonesaar-lang`.

## Images

Screenshots live in `img/` as WebP at 360 and 540 px wide (`name-360.webp`, `name-540.webp`); use `srcset`, `width="540" height="1200"` and `loading="lazy"` below the fold.

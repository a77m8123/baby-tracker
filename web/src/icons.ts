// Иконки (по мотивам Material Icons из Android-версии), инлайн-SVG.

const svg = (path: string) =>
  `<svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true" fill="currentColor">${path}</svg>`

export const icon = {
  moon: svg('<path d="M12.3 2a10 10 0 1 0 9.7 12.4A8 8 0 0 1 12.3 2z"/>'),
  sun: svg('<circle cx="12" cy="12" r="4.5"/><path d="M12 1.5v3M12 19.5v3M1.5 12h3M19.5 12h3M4.6 4.6l2.1 2.1M17.3 17.3l2.1 2.1M4.6 19.4l2.1-2.1M17.3 6.7l2.1-2.1" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>'),
  baby: svg('<circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" stroke-width="2"/><circle cx="9" cy="11" r="1.3"/><circle cx="15" cy="11" r="1.3"/><path d="M8.8 14.6a4 4 0 0 0 6.4 0" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>'),
  bottle: svg('<path d="M10 2h4v2.2l1.5 1.8H8.5L10 4.2V2zM8 7h8v13a2 2 0 0 1-2 2h-4a2 2 0 0 1-2-2V7zm2 4v1.5h4V11h-4zm0 3.5V16h4v-1.5h-4z"/>'),
  spoon: svg('<path d="M8.1 13.3 2.6 18.8a1.5 1.5 0 0 0 2.1 2.1l5.5-5.5c1.6.7 3.6.3 5.2-1.3 2.2-2.2 2.7-5.5 1-7.2s-5-1.2-7.2 1c-1.6 1.6-2 3.6-1.3 5.2l.2.2z"/><path d="m15 15 6 6" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"/>'),
  diaper: svg('<path d="M3 6h18v3c0 6-4 10-9 10S3 15 3 9V6zm2 3c.3 1.8 1.8 3 3.5 3H9V8H5v1zm14 0V8h-4v4h.5c1.7 0 3.2-1.2 3.5-3z"/>'),
  sound: svg('<path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3A4.5 4.5 0 0 0 14 8v8a4.5 4.5 0 0 0 2.5-4zM14 3.2v2.1a7 7 0 0 1 0 13.4v2.1a9 9 0 0 0 0-17.6z"/>'),
  air: svg('<path d="M3 8h10a3 3 0 1 0-3-3M3 12h15a3 3 0 1 1-3 3M3 16h7" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>'),
  drop: svg('<path d="M12 2.5S5.5 10 5.5 14.5a6.5 6.5 0 0 0 13 0C18.5 10 12 2.5 12 2.5z"/>'),
  wave: svg('<path d="M4 10v4M8 6v12M12 3v18M16 7v10M20 10v4" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"/>'),
  note: svg('<path d="M12 3v10.6A4 4 0 1 0 14 17V7h4V3h-6z"/>'),
  stop: svg('<rect x="6" y="6" width="12" height="12" rx="2"/>'),
  back: svg('<path d="M15.5 4.5 8 12l7.5 7.5" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/>'),
}

// Small inline icon set (stroke icons, 24x24).
const P = { width: 22, height: 22, viewBox: "0 0 24 24", fill: "none", stroke: "currentColor", strokeWidth: 2, strokeLinecap: "round" as const, strokeLinejoin: "round" as const };

export const IconHome = () => (<svg {...P}><path d="M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z" /></svg>);
export const IconList = () => (<svg {...P}><path d="M8 6h13M8 12h13M8 18h13M3.5 6h.01M3.5 12h.01M3.5 18h.01" /></svg>);
export const IconSwap = () => (<svg {...P}><path d="M7 4L3 8l4 4M3 8h14M17 20l4-4-4-4M21 16H7" /></svg>);
export const IconLive = () => (<svg {...P}><circle cx="12" cy="12" r="3" /><path d="M6.3 6.3a8 8 0 0 0 0 11.4M17.7 6.3a8 8 0 0 1 0 11.4M3.5 3.5a12 12 0 0 0 0 17M20.5 3.5a12 12 0 0 1 0 17" /></svg>);
export const IconGrid = () => (<svg {...P}><rect x="3" y="3" width="7" height="7" rx="2" /><rect x="14" y="3" width="7" height="7" rx="2" /><rect x="3" y="14" width="7" height="7" rx="2" /><rect x="14" y="14" width="7" height="7" rx="2" /></svg>);
export const IconUser = () => (<svg {...P}><circle cx="12" cy="8" r="4" /><path d="M4 21a8 8 0 0 1 16 0" /></svg>);
export const IconBack = () => (<svg {...P}><path d="M15 18l-6-6 6-6" /></svg>);
export const IconClose = () => (<svg {...P} width={30} height={30}><path d="M6 6l12 12M18 6L6 18" /></svg>);
export const IconChevron = ({ open }: { open?: boolean }) => (<svg {...P} style={{ transform: open ? "rotate(180deg)" : undefined, transition: "transform .15s" }}><path d="M6 9l6 6 6-6" /></svg>);
export const IconRefresh = () => (<svg {...P}><path d="M21 12a9 9 0 1 1-3-6.7L21 8M21 3v5h-5" /></svg>);
export const IconBolt = () => (<svg {...P}><path d="M13 2L4 14h7l-1 8 9-12h-7z" /></svg>);
export const IconShirt = () => (<svg {...P}><path d="M8 3l4 2 4-2 5 4-3 3-2-1v12H8V9l-2 1-3-3z" /></svg>);
export const IconWarn = () => (<svg {...P} width={16} height={16}><path d="M12 3l10 18H2zM12 10v4M12 17h.01" /></svg>);
export const IconCheck = () => (<svg {...P} width={16} height={16}><path d="M5 12l5 5 9-10" /></svg>);

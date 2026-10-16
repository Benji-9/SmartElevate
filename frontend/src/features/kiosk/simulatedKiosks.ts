// ponytail: credenciales de los kioscos simulados en memoria (por ascensor) mientras dure la
// pestaña: volver al simulador reutiliza el kiosco; al recargar se crea otro y los viejos se
// revocan desde /admin/kioscos.
export const simulatedKiosks = new Map<string, string>();

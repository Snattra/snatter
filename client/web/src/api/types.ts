import type { components } from "./schema";

type Schemas = components["schemas"];

export type Account = Schemas["Account"];
export type ApiErrorBody = Schemas["ApiError"];
export type AuthResponse = Schemas["AuthResponse"];
export type Challenge = Schemas["Challenge"];
export type Channel = Schemas["Channel"];
export type Message = Schemas["Message"];
export type PermissionSet = Schemas["PermissionSet"];
export type Presence = Schemas["Presence"];
export type ReadState = Schemas["ReadState"];
export type Role = Schemas["Role"];
export type ServerInfo = Schemas["ServerInfo"];
export type SystemMessage = Schemas["SystemMessage"];
export type UserMessage = Schemas["UserMessage"];

export type GatewayClientFrame = Schemas["GatewayClientFrame"];
export type GatewayServerFrame = Schemas["GatewayServerFrame"];
export type GatewayCloseReason = Schemas["GatewayCloseReason"];

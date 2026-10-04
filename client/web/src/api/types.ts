import type { components } from "./schema";

type Schemas = components["schemas"];

export type Account = Schemas["Account"];
export type ApiErrorBody = Schemas["ApiError"];
export type AuthResponse = Schemas["AuthResponse"];
export type Ban = Schemas["Ban"];
export type Challenge = Schemas["Challenge"];
export type Channel = Schemas["Channel"];
export type ChannelCreate = Schemas["ChannelCreate"];
export type ChannelType = Schemas["ChannelType"];
export type ChannelUpdate = Schemas["ChannelUpdate"];
export type Invite = Schemas["Invite"];
export type InvitePreview = Schemas["InvitePreview"];
export type DeletedMessage = Schemas["DeletedMessage"];
export type Message = Schemas["Message"];
export type Permission = Schemas["Permission"];
export type PermissionSet = Schemas["PermissionSet"];
export type Presence = Schemas["Presence"];
export type RateLimitPolicy = Schemas["RateLimitPolicy"];
export type RateLimits = Schemas["RateLimits"];
export type ReadState = Schemas["ReadState"];
export type RegistrationMode = Schemas["RegistrationMode"];
export type Role = Schemas["Role"];
export type ServerInfo = Schemas["ServerInfo"];
export type ServerSettings = Schemas["ServerSettings"];
export type ServerSettingsUpdate = Schemas["ServerSettingsUpdate"];
export type SystemMessage = Schemas["SystemMessage"];
export type UserMessage = Schemas["UserMessage"];
export type VoiceEndReason = Schemas["VoiceEndReason"];
export type VoiceRefusal = Schemas["VoiceRefusal"];
export type VoiceState = Schemas["VoiceState"];

export type GatewayClientFrame = Schemas["GatewayClientFrame"];
export type GatewayServerFrame = Schemas["GatewayServerFrame"];
export type GatewayCloseReason = Schemas["GatewayCloseReason"];

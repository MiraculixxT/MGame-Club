package de.miraculixx.mgames.modules.games.chess

enum class FieldsChess(val white: Boolean, val light: String, val dark: String) {
    EMPTY(false,"⬜", "\uD83D\uDFEB"),

    PAWN_WHITE(true, "<:LWP:990584759291895899>", "<:DWP:990584184391233536>"),
    PAWN_BLACK(false, "<:LBP:990309537426858044>", "<:DBP:990309837739008030>"), //Bauer

    KNIGHT_WHITE(true, "<:LWK:990679024550424586>", "<:DWK:990677974166679602>"),
    KNIGHT_BLACK(false, "<:LBK:990678742118576168>", "<:DBK:990676755054461028>"), //Springer

    BISHOP_WHITE(true, "<:LWB:990674371960000565>", "<:DWB:990673626737041489>"),
    BISHOP_BLACK(false, "<:LBB:990670367326601296>", "<:DBB:990671405479788625>"), //Läufer

    ROOK_WHITE(true, "<:LWR:990667993686110218>", "<:DWR:990667591007735848>"),
    ROOK_BLACK(false, "<:LBR:990666348172546169>", "<:DBR:990667122944393316>"), //Turm

    QUEEN_WHITE(true, "<:LWQ:990721310327980080>", "<:DWQ:990720742947700767>"),
    QUEEN_BLACK(false, "<:LBQ:990719072025051186>", "<:DBQ:990719812105830422>"),

    KING_WHITE(true, "<:LWW:990716537612685412>", "<:DWW:990715766653460593>"),
    KING_BLACK(false, "<:LBW:990708610038382622>", "<:DBW:990711543895318608>")
}
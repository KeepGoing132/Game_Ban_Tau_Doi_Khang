package common.protocol;

public enum MessageType {
    // 1. Auth & User
    LOGIN_REQ,
    LOGIN_RESP,
    LOGOUT_REQ,
    GET_ONLINE_USERS_REQ,
    ONLINE_USERS_RESP,
    USER_STATUS_BROADCAST,

    // 2. Lobby & Room
    CREATE_ROOM_REQ,
    CREATE_ROOM_RESP,
    JOIN_ROOM_REQ,
    JOIN_ROOM_RESP,
    LEAVE_ROOM_REQ,
    ROOM_USER_JOINED,
    ROOM_USER_LEFT,

    // 3. Invite & Matchmaking
    INVITE_REQ,
    INVITE_NOTIFY,
    INVITE_RESPOND_REQ,
    INVITE_RESULT,
    QUEUE_JOIN_REQ,
    QUEUE_LEAVE_REQ,
    MATCH_FOUND,

    // 4. Ship Placement & Ready
    PLACE_SHIPS_REQ,
    PLAYER_READY_REQ,
    OPPONENT_READY_NOTIFY,
    MATCH_START,

    // 5. Battle & Turn
    FIRE_REQ,
    FIRE_RESULT,
    TURN_CHANGE,
    TIMEOUT_NOTIFY,

    // 6. End Game & Rematch
    SURRENDER_REQ,
    MATCH_END,
    REMATCH_REQ,
    REMATCH_NOTIFY,
    REMATCH_CONFIRM_REQ,
    REMATCH_START,

    // 7. Leaderboard
    GET_LEADERBOARD_REQ,
    LEADERBOARD_RESP,

    // 8. General
    ERROR_NOTIFY,
    CHAT_MSG
}

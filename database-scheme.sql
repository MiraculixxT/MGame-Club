CREATE TABLE IF NOT EXISTS guildData (
    Discord_ID BIGINT PRIMARY KEY,
    Premium BOOLEAN NOT NULL,
    Stats_Channel BIGINT NOT NULL,
    Language VARCHAR(10) NOT NULL
);

CREATE TABLE IF NOT EXISTS userData (
    ID INT AUTO_INCREMENT PRIMARY KEY,
    Guild_ID BIGINT NOT NULL,
    Discord_ID BIGINT NOT NULL,
    Coins INT NOT NULL,
    Total_Coins INT NOT NULL,
    FOREIGN KEY (Guild_ID) REFERENCES guildData(Discord_ID)
);

CREATE UNIQUE INDEX idx_user_unique ON userData(Guild_ID, Discord_ID);

CREATE TABLE IF NOT EXISTS userEmotes (
    ID INT NOT NULL,
    Emote_Type VARCHAR(255) NOT NULL,
    Emote VARCHAR(255) NOT NULL,
    FOREIGN KEY (ID) REFERENCES userData(ID)
);

CREATE TABLE IF NOT EXISTS userEmotesActive (
    ID INT PRIMARY KEY,
    C4_P VARCHAR(255) NOT NULL,
    C4_S VARCHAR(255) NOT NULL,
    FOREIGN KEY (ID) REFERENCES userData(ID)
);

CREATE TABLE IF NOT EXISTS userStats (
    Discord_ID BIGINT NOT NULL,
    Game_ID TINYINT UNSIGNED NOT NULL,
    Mode_ID TINYINT UNSIGNED NOT NULL,
    Difficulty TINYINT UNSIGNED NOT NULL,
    Wins INT UNSIGNED NOT NULL,
    Losses INT UNSIGNED NOT NULL,
    PRIMARY KEY (Discord_ID, Game_ID, Mode_ID, Difficulty),
    INDEX idx_stats_leaderboard (Game_ID, Mode_ID, Difficulty, Wins, Losses)
);

CREATE TABLE IF NOT EXISTS gameHistory (
    Played_At BIGINT NOT NULL,
    Game_ID TINYINT UNSIGNED NOT NULL,
    Discord_ID BIGINT NOT NULL,
    INDEX idx_history_game_time (Game_ID, Played_At),
    INDEX idx_history_user_time (Discord_ID, Played_At)
);

CREATE TABLE IF NOT EXISTS userDailyPlay (
    Discord_ID BIGINT NOT NULL,
    Game VARCHAR(64) NOT NULL,
    Last_Play_Date VARCHAR(10) NOT NULL,
    Streak INT NOT NULL,
    PRIMARY KEY (Discord_ID, Game)
);

CREATE TABLE IF NOT EXISTS globalDaily (
    Date VARCHAR(10) PRIMARY KEY,
    Seed BIGINT NOT NULL,
    Trivia TEXT
);

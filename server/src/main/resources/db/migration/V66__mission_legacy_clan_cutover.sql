-- Only clans present at cutover retain the old single quest. New clans use the managed CLAN pool.
CREATE TABLE mission_legacy_clans (
 clan_id UUID PRIMARY KEY REFERENCES clans(id) ON DELETE CASCADE,
 target INTEGER NOT NULL CHECK(target>0), reward_xp INTEGER NOT NULL CHECK(reward_xp>=0),
 reward_gold INTEGER NOT NULL CHECK(reward_gold>=0), reward_gems INTEGER NOT NULL CHECK(reward_gems>=0)
);
INSERT INTO mission_legacy_clans(clan_id,target,reward_xp,reward_gold,reward_gems)
 SELECT id,quest_target,quest_reward_xp,quest_reward_gold,quest_reward_gems FROM clans;
CREATE TABLE mission_legacy_clan_members (
 clan_id UUID NOT NULL REFERENCES mission_legacy_clans(clan_id) ON DELETE CASCADE,
 user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 contribution INTEGER NOT NULL CHECK(contribution>=0), claimed BOOLEAN NOT NULL,
 PRIMARY KEY(clan_id,user_id)
);
INSERT INTO mission_legacy_clan_members(clan_id,user_id,contribution,claimed)
 SELECT clan_id,user_id,quest_contribution,quest_reward_claimed FROM clan_members;

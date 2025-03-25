package io.masterkun.axor.cluster.membership;

import io.masterkun.axor.cluster.LocalMemberState;
import io.masterkun.axor.cluster.MemberState;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DefaultSplitBrainResolver implements SplitBrainResolver {
    private final int minRequireMembers;
    private final int minInitialMembers;
    private final List<FilterEntry<?>> filters;
    private int aliveMemberCount = 0;
    private boolean initialState = true;

    public DefaultSplitBrainResolver(int minRequireMembers,
                                     int minInitialMembers) {
        this.minRequireMembers = minRequireMembers;
        this.minInitialMembers = minInitialMembers;
        this.filters = Collections.emptyList();
    }

    public DefaultSplitBrainResolver(int minRequireMembers,
                                     int minInitialMembers,
                                     List<FilterEntry<?>> filters) {
        this.minRequireMembers = minRequireMembers;
        this.minInitialMembers = minInitialMembers;
        this.filters = filters.isEmpty() ? Collections.emptyList() : new ArrayList<>(filters);
    }

    @VisibleForTesting
    void setAliveMemberCount(int aliveMemberCount) {
        this.aliveMemberCount = aliveMemberCount;
    }

    @VisibleForTesting
    void setInitialState(boolean initialState) {
        this.initialState = initialState;
    }

    private boolean match(Member member) {
        if (!filters.isEmpty()) {
            MetaInfo metaInfo = member.metaInfo();
            for (FilterEntry<?> filter : filters) {
                if (!filter.matches(metaInfo)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void onMemberUpdate(Member from, Member to) {
        if (match(from)) {
            if (!match(to)) {
                aliveMemberCount--;
            }
        } else if (match(to)) {
            aliveMemberCount++;
            if (initialState && aliveMemberCount >= minInitialMembers) {
                initialState = false;
            }
        }
    }

    @Override
    public void onMemberStateChange(Member member, MemberState from, MemberState to) {
        if (!match(member)) {
            return;
        }
        if (from.ALIVE) {
            if (!to.ALIVE) {
                aliveMemberCount--;
            }
        } else if (to.ALIVE) {
            aliveMemberCount++;
            if (initialState && aliveMemberCount >= minInitialMembers) {
                initialState = false;
            }
        }
    }

    @Override
    public LocalMemberState getLocalMemberState() {
        if (aliveMemberCount >= minRequireMembers) {
            return initialState ? LocalMemberState.WEAKLY_UP : LocalMemberState.UP;
        } else if (aliveMemberCount > 0) {
            return LocalMemberState.WEAKLY_UP;
        } else {
            return LocalMemberState.DISCONNECTED;
        }
    }

    @Override
    public String name() {
        return "MinRequireMembers";
    }

    public record FilterEntry<T>(MetaKey<T> key, T value) {
        public boolean matches(MetaInfo metaInfo) {
            return key.contains(metaInfo, value);
        }
    }
}

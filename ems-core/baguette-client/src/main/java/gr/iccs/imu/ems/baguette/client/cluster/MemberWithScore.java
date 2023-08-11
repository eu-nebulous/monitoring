/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client.cluster;

import io.atomix.cluster.Member;
import lombok.Data;

@Data
public class MemberWithScore implements Comparable<MemberWithScore> {
    public final static MemberWithScore NULL_MEMBER = new MemberWithScore(null, 0);

    private final Member member;
    private final double score;

    private MemberWithScore(Member m, double s) {
        member = m;
        score = s;
    }

    public MemberWithScore(Member m, MemberScoreFunction scoreFunction) {
        member = m;
        score = scoreFunction.apply(m);
    }

    @Override
    public int compareTo(MemberWithScore o) {
        double score1 = this.getScore();
        double score2 = o.getScore();
        int result = (int) Math.signum(score1 - score2);
        if (result == 0) {
            String uuid1 = this.getMember().properties().getProperty("uuid", "0");
            String uuid2 = o.getMember().properties().getProperty("uuid", "0");
            result = uuid1.compareTo(uuid2);
        }
        return result;
    }
}

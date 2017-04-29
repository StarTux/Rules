package com.winthier.rules;

import java.util.Random;
import java.util.UUID;
import lombok.Value;

@Value
public class Password {
    private final UUID uuid;
    private final String pw;

    public static Password of(UUID uuid, int salt) {
        int seed = uuid.hashCode() * salt;
        Random rnd = new Random((long)seed);
        String pw = "";
        for (int i = 0; i < 5; ++i) {
            int n = rnd.nextInt(10 + 26);
            int c = (int)'-';
            if (n < 10) {
                c = '0' + n;
            } else {
                c = 'a' + (n - 10);
            }
            pw += (char)c;
        }
        return new Password(uuid, pw);
    }
}

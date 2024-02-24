package com.github.neapovil.twofactor.object;

import java.time.Instant;

import com.j256.twofactorauth.TimeBasedOneTimePasswordUtil;

public final class TwoFactorSetup
{
    public String secret = TimeBasedOneTimePasswordUtil.generateBase32Secret();
    public Instant started = Instant.now();
    public int validForMinutes = 5;
}

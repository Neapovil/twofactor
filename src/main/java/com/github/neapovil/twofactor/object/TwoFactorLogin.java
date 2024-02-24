package com.github.neapovil.twofactor.object;

public final class TwoFactorLogin
{
    public int attempts = 0;
    public int maxAttempts = 3;
    public int validForMinutes = 2;
}

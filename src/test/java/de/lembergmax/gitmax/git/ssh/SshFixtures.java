package de.lembergmax.gitmax.git.ssh;

/**
 * Wegwerf-Schlüssel nur für Tests, mit {@code ssh-keygen} erzeugt. Sie schützen nichts und gehören zu keinem Konto.
 */
final class SshFixtures {
    private SshFixtures() {
    }

    static final String ED25519_PLAIN =
            "-----BEGIN OPENSSH PRIVATE KEY-----\n" +
            "b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAMwAAAAtzc2gtZW\n" +
            "QyNTUxOQAAACBzEtmNcc/TnqhksNDyqQyiCD4f0VKoEP6sQFq5UE6rmQAAAJAlYqWnJWKl\n" +
            "pwAAAAtzc2gtZWQyNTUxOQAAACBzEtmNcc/TnqhksNDyqQyiCD4f0VKoEP6sQFq5UE6rmQ\n" +
            "AAAEAIi/Ltnahpk7VOJLNE3xhLkMC+d0DUPRFbq8pNz3jJCnMS2Y1xz9OeqGSw0PKpDKII\n" +
            "Ph/RUqgQ/qxAWrlQTquZAAAADWZpeHR1cmUtcGxhaW4=\n" +
            "-----END OPENSSH PRIVATE KEY-----\n";

    static final String ED25519_PLAIN_PUBLIC = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIHMS2Y1xz9OeqGSw0PKpDKIIPh/RUqgQ/qxAWrlQTquZ fixture-plain";

    static final String ED25519_PLAIN_FINGERPRINT = "SHA256:GravXTmECT7eJmq9kr4gem/7659W19Gos/2ZsMk8D1M";

    static final String ED25519_PROTECTED =
            "-----BEGIN OPENSSH PRIVATE KEY-----\n" +
            "b3BlbnNzaC1rZXktdjEAAAAACmFlczI1Ni1jdHIAAAAGYmNyeXB0AAAAGAAAABA44XYMXR\n" +
            "LvirMJBPR5nYPxAAAAGAAAAAEAAAAzAAAAC3NzaC1lZDI1NTE5AAAAIK9p/5er0ePShagO\n" +
            "AGB8V+9CjgFRXuaJY/cNvAsIHNOoAAAAoKf55rKmPas/EXayMtRxILkrzO75OFETmq8AgI\n" +
            "cWtx0ZhQmGGxgiKIbTvQhhpT5pth8SZ9+sfI9uRcj367OrC8+d/lpKaB8AcVXF4jPFMSom\n" +
            "F0ME33JuSYibTTwGOUgkyFhA7EKldjXqLMJhjfYvHTSoZRTH0yZ+4Qhmv6y3yXsx2q7RP0\n" +
            "EDB6ix8bkdOs7xkFn/PBFSu5fBp5F5RXBKoMo=\n" +
            "-----END OPENSSH PRIVATE KEY-----\n";

    static final String ED25519_PROTECTED_PUBLIC = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIK9p/5er0ePShagOAGB8V+9CjgFRXuaJY/cNvAsIHNOo fixture-protected";

    static final String ED25519_PROTECTED_FINGERPRINT = "SHA256:rIpnmNs1MYOaQR7xJWBQu2Tw5ZenvCk5kqpMdNb2+iw";

    static final String RSA_PEM =
            "-----BEGIN RSA PRIVATE KEY-----\n" +
            "MIIEowIBAAKCAQEAqzYfT0Ts5C7ZYoe720AXSXK2t6ytYJYKUAvyQdgPcqhdllEW\n" +
            "Z8tiDdb821LdBjQY5pJxQdLf8/OOKuOrX9KSpyyQYg6KRjswusDWR4Lg33Ax/CKJ\n" +
            "GC/R850uxNkHPJd5pHLuXI73NPk4a7M2ZfPIjkxe3IZsjNdEvSEwFpfE0l4lzn/O\n" +
            "euLJHWB1gF94gWg170R3UZj4bJwc7sAU7CNPGzzykI0J39mIT4My87DGd0jt30I4\n" +
            "6r21tmPjWfHJyJF0dtLCC+cMTmtsLxnUVZl+S6fYS5gSUMZTqs5yjNZMOlcW4Gag\n" +
            "1rsTmgXAXqffXDU2Mxw6MZHYQXX8PfffljoYKQIDAQABAoIBABTYOwmgn6LMXWUU\n" +
            "l3eDHxKvJ7XHDTjEK69BNRZ1IrRX8UT6zFqItYBv20sslIJL13hC/g0Aiomd7oIk\n" +
            "Q9ofvgEieNWnZYhVboJeNfhw48XwV/WVktFiCRK64VXQAoSLAKFX6NNWCJzndsoO\n" +
            "AKJ7AsDoZWg96XtgPhs0rjEh+zGNV9n1t+O7SfColHPKlgiEFZrGS4HsGL/iGTDJ\n" +
            "SKuGRCwlESpm2jrHtv1pEQOmPADaKcbo2Qz7dj0B2OMolk6us7v8eEBiT2njCHpY\n" +
            "zYI292A1lB9vY0S0mRMdsNeh91XyWlgyisWB1CA1C16D8WZZjiZ9O/HBQg+piEEZ\n" +
            "JpFd/B0CgYEA1ZiODDeHyPOzok6BFmHWec9zjPxN8gHndXuyE45oDbPZZixLgji8\n" +
            "y9Ev3bWTjWJVQwu4EYHd652MOJCbo4b0Fk7U/inG7XxLXoHVIu0ut2njLvcAEmA1\n" +
            "88f3ZS+mvyITKhkgA2eVLN/YaY53YMti043IY2+7nI8ve4ewfBexSBcCgYEAzTN8\n" +
            "2Uj/HzjVkYGpMXr5M937g5/mzfMBG8Iov7pcU/ep9puBIfUkkn2/Bq1wPGpzcEUx\n" +
            "qrxfekGmnR5P+W0Oc4jIZseS5GOc4aUkW1NKWlXZsrLXP02UlaseBhpDowsOfs7i\n" +
            "KQfyKUTOvICUFid9ooYpUDp3jv1VXEa1gy1Hib8CgYA3b2WGdC8Qj3dSq49DMNdK\n" +
            "O/YgZCcSpT3eNuFLxAzraX8Fzn82Z+VP/JWws/x8mEXKpdL68DqZeQU4dQd/1Hmr\n" +
            "BICxCkSvxC+HcqjPbMFQJvIocUCahE/cWkyx/UEIoB5bMbQmTg7gW6Q+GRSQkZiv\n" +
            "kT+t1wTZKCxJojjCrB99twKBgCltXqRDb3YvNDbHFZBuwkbtZuzA6IjlqXCgNzfV\n" +
            "+PPeyUqHRH/FjReiGWHQvBsGZr3gylEs7J2zCV8pEn5JvSQoFkVhv08qqS6I95kU\n" +
            "bKtmL6g7IOef0wKQZGRZAxS0k72YKOKdvw8D3DUERGFgoaWhLlALLb4JeSVDBTwx\n" +
            "dQLjAoGBAIgXEHiBonjwaeVckQeDYoNuAPLS2wthhmBiuvJYwVnsxtu1zs1WP6Ud\n" +
            "TxuiWHaPJbstF9pr3R9DLoVX1ovnJPOiwv9A2riczjIfeqCj9a2EdZIRWFbrD4ud\n" +
            "fRgvw5EVMftHFEPfwsw7tZil+kFJnI0QJiFRgkL9+Bx60d9jNxlh\n" +
            "-----END RSA PRIVATE KEY-----\n";

    static final String RSA_PEM_PUBLIC = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQCrNh9PROzkLtlih7vbQBdJcra3rK1glgpQC/JB2A9yqF2WURZny2IN1vzbUt0GNBjmknFB0t/z844q46tf0pKnLJBiDopGOzC6wNZHguDfcDH8IokYL9HznS7E2Qc8l3mkcu5cjvc0+ThrszZl88iOTF7chmyM10S9ITAWl8TSXiXOf8564skdYHWAX3iBaDXvRHdRmPhsnBzuwBTsI08bPPKQjQnf2YhPgzLzsMZ3SO3fQjjqvbW2Y+NZ8cnIkXR20sIL5wxOa2wvGdRVmX5Lp9hLmBJQxlOqznKM1kw6VxbgZqDWuxOaBcBep99cNTYzHDoxkdhBdfw999+WOhgp fixture-rsa";

    static final String RSA_PEM_FINGERPRINT = "SHA256:8HkMW6qrbg8dVhoRYL0PaZyGWqf7NjColf851KLnDlY";

    static final String ECDSA =
            "-----BEGIN OPENSSH PRIVATE KEY-----\n" +
            "b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAaAAAABNlY2RzYS\n" +
            "1zaGEyLW5pc3RwMjU2AAAACG5pc3RwMjU2AAAAQQTQU477eyJg1Mm6wU3yRtT4cjPz3Z7k\n" +
            "t6m9+HWoPSzINZyvIEzTLByFUukw6IaXA1Geqe47f99YQx78LL5wcP8yAAAAqCVv/AQlb/\n" +
            "wEAAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBNBTjvt7ImDUybrB\n" +
            "TfJG1PhyM/PdnuS3qb34dag9LMg1nK8gTNMsHIVS6TDohpcDUZ6p7jt/31hDHvwsvnBw/z\n" +
            "IAAAAhAJlh3zDV1b7+hhwqFShjfNo6zBwEuMopeN3Q8YJlax4BAAAADWZpeHR1cmUtZWNk\n" +
            "c2EBAg==\n" +
            "-----END OPENSSH PRIVATE KEY-----\n";

    static final String ECDSA_PUBLIC = "ecdsa-sha2-nistp256 AAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBNBTjvt7ImDUybrBTfJG1PhyM/PdnuS3qb34dag9LMg1nK8gTNMsHIVS6TDohpcDUZ6p7jt/31hDHvwsvnBw/zI= fixture-ecdsa";

    static final String ECDSA_FINGERPRINT = "SHA256:S8raPZmDWOEbaHhJ5b8wsL/1V5PiqQngZBDNz9g/jHk";

    static final String PROTECTED_PASSPHRASE = "geheim-123";
}

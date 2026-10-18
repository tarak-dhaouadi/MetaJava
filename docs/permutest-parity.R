# Side-by-side check of MetaJava's permutation test against metafor (written against the metafor 4.6-0 sources).
# NOTE: metafor stops Fisher scoring at a change <= 1e-5, so its REML tau2 (0.02448828) differs by ~3.5e-6 from MetaJava (0.02448478, the exact root); use rma(..., control = list(threshold = 1e-12)) to compare the fits at full precision. The permutation p-values agree either way.
# Run in R with metafor installed; compare with the numbers MetaJava gives (SelfTest.java pins them).
# Data: k = 8, binary moderator, GENQ weights of zero on five studies, so that some permuted fits fail
# (qr.solve: "singular matrix 'a' in solve") -> metafor redraws them.
# Expected from MetaJava (iter = 40, seed = 123): 48 draws, 8 failed, 40 valid; p = 0.425 (intercept), 0.575 (slope), omnibus 0.575.
# Exact test: 70 unique permutations (8!/(4! 4!)), 10 fail; p = 0.5 (intercept), 2/3 (slope and omnibus).
library(metafor)
yi <- c(0.12, 0.48, 0.31, 0.95, 0.66, 1.10, 0.40, 0.83); vi <- c(0.20, 0.25, 0.18, 0.30, 0.22, 0.28, 0.24, 0.19)^2
x  <- c(0, 0, 0, 0, 1, 1, 1, 1); w <- c(1, 1, 0, 0, 1, 0, 0, 0)
res <- rma(yi, vi, mods = ~ x, weights = w, method = "GENQ")
print(res$tau2); print(res$zval); print(res$QM)

iter <- 40; seed <- 123
print(permutest(res, exact = "i"))   # number of unique permutations: 70 (> iter = 40, so the test below is random)
pt <- permutest(res, exact = FALSE, iter = iter, seed = seed, progbar = FALSE)
print(pt$pval); print(pt$QMp)

# permutest() does not report how many draws failed; this replays its loop (same RNG stream) and counts them
X <- as.data.frame(res$X)[do.call(order, as.data.frame(res$X)), ]
set.seed(seed); valid <- 0; draws <- 0; failed <- 0
while (valid < iter) {
  r <- try(suppressWarnings(rma(yi, vi, weights = w, mods = cbind(X[sample(res$k), ]), intercept = FALSE, method = "GENQ", skipr2 = TRUE)), silent = TRUE)
  draws <- draws + 1
  if (inherits(r, "try-error")) { failed <- failed + 1; next }
  valid <- valid + 1
}
cat("draws:", draws, " failed:", failed, " valid:", valid, "\n")

pe <- permutest(res, exact = TRUE, progbar = FALSE)
print(pe$pval); print(pe$QMp)
# exact decided by the number of unique permutations: with iter >= 70 the test is exact without exact = TRUE
print(permutest(res, iter = 100, progbar = FALSE)$pval)

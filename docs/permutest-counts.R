# Number of unique permutations of the model-matrix rows: metafor::permutest(res, exact = "i") against MetaJava (Perm.plan).
# exact = "i" returns metafor's X.exact.iter immediately, before any array is allocated or any model is refitted.
# MetaJava prints the same number (exactly, with arbitrary precision); expected values in the comments (R prints doubles).
library(metafor)
set.seed(1)
cnt <- function(k, mods) {
  yi <- rnorm(k, 0.3, 0.3); vi <- runif(k, 0.02, 0.06)
  res <- rma(yi, vi, mods = mods, method = "REML")
  permutest(res, exact = "i")
}
print(cnt(10,  ~ factor(rep(0:1, c(5, 5)))))            # 252
print(cnt(30,  ~ factor(rep(0:1, c(15, 15)))))          # 155117520
print(cnt(30,  ~ factor(rep(0:2, c(10, 10, 10)))))      # 5550996791340
print(cnt(12,  ~ seq_len(12)))                          # 479001600     (all rows differ: k!)
print(cnt(25,  ~ seq_len(25)))                          # 1.551121e+25  (metafor: double precision; MetaJava: 15511210043330985984000000)
print(cnt(40,  ~ factor(rep(0:1, c(20, 20)))))          # 137846528820
print(cnt(200, ~ factor(rep(0:1, c(100, 100)))))        # 9.054851e+58  (MetaJava: 90548514656103281165404177077484163874504589675413336841320)
print(cnt(180, ~ seq_len(180)))                         # Inf           (180! overflows a double; MetaJava: exact, 330 digits)
# What an exact test of the 15 + 15 design would ask of metafor (from the source: matrices of X.exact.iter rows are allocated for beta, zval and QM,
# and .genuperms() builds the whole X.exact.iter x k permutation matrix with rbind): 155,117,520 x 30 doubles = 37 GB for the permutations alone.

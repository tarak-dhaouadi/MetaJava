# MetaJava — copyright and licensing

MetaJava is distributed under the GNU General Public License, version 2 or (at your option) any later version
(GPL-2.0-or-later); see [LICENSE.md](LICENSE.md). It re-implements, in Java, methods of the R packages **meta**
and **metafor**, both distributed under the GPL (>= 2); the licence of MetaJava is chosen to be compatible with them.

## Copyright holders

- Tarak Dhaouadi — MetaJava (the Java implementation, the Swing user interface, the plots, tests and documentation).
- Guido Schwarzer (and co-authors of the R package meta) — the methods, argument names, option names and
  output conventions of **meta** that MetaJava follows.
- Wolfgang Viechtbauer — the R package **metafor** (random/mixed-effects models, `rma.glmm`, outlier and
  influence diagnostics) whose methods MetaJava follows.

## What follows which R function

| Java source (`src/main/java/org/metajava/…`) | Follows |
|---|---|
| `core/Effects.java`, `core/Pool.java`, `core/Meta.java`, `core/Report.java` | `metagen`, `metabin`, `metacont`, `metacor`, `metainc`, `metamean`, `metaprop`, `metarate`; `print(summary())`; `calcPI` prediction intervals (V, HTS, HK, HK-PR, KR, KR-PR, NNF, S); `kenwardroger`; `metacum`, `metainf` |
| `core/Rma.java`, `core/Reg.java` | `metafor::rma.uni` (tau² estimators, confidence intervals for tau², H/I²); `meta::metareg` |
| `core/Glmm.java` | `metafor::rma.glmm(method = "ML")` as used by `meta` (`method = "GLMM"`) |
| `core/Lrp.java` | penalised logistic regression of `meta::metabin(method = "LRP")` |
| `core/Bias.java`, `core/TrimFill.java` | `meta::metabias`, `meta::trimfill` |
| `core/PropCI.java` | confidence intervals for single proportions (`meta::binconf` methods) |
| `core/Influence.java` | `metafor::influence.rma.uni` (Viechtbauer & Cheung, 2010) |
| `plot/Plots.java` | `meta::forest` and `funnel`, `metafor::plot.infl.rma.uni`, `baujat`, `radial` |

The distribution functions (`core/Dist.java`), linear algebra helpers (`core/Lin.java`), data readers
(`io/TableReader.java`, using the JDK's own zip/XML parsers) and the Swing / Java2D user interface use only the
Java standard library.

The reference numbers in `src/test/java/org/metajava/SelfTest.java` are taken from forest plots printed by the R
package meta. The bundled example data (`src/main/resources/examples/HR_meta.xlsx`) are illustrative data with
generic study labels.

If you use MetaJava, please also cite meta and metafor (see [CITATION.cff](CITATION.cff)).

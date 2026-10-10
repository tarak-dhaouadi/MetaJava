# Changelog

## Unreleased

### Fixed - order of the diagnostics in the output
- The output now follows the order of section 10 (outlier & influence diagnostics, Baujat, radial, L’Abbé, GOSH): the GOSH analysis and plot are no longer shown first.

### Fixed - no sideways scrolling in the text output
- Long lines of the verbose output sections (method details, notes, references, warnings) are now wrapped, with a hanging indent for list items, to the current width of the results panel and re-wrapped when the window is resized. Tables with aligned columns are left untouched, and the saved .txt / Excel / Word exports still use the original text.

### New - permutation test for meta-regression (`permutest`)
- Section 6 **Permutation test (permutest)**: as `metafor::permutest.rma.uni`, the rows of the model matrix are permuted, the model is refitted (tau^2 re-estimated every time) and the permutation p-values of the coefficients (`pval*`) and of the omnibus test of the moderators are printed next to the model-based ones. With the Hartung-Knapp option the t and F statistics are permuted, as in metafor.
- Random permutations (default 5000, spinner; seed field; permutations are drawn sequentially from one seeded generator, so results do not depend on the number of threads) with p = (r + 1) / (B + 1) (Phipson and Smyth, 2010), or the **exact** test over all k! permutations when k <= 9 (also used automatically when k! does not exceed the number of permutations requested).
- Checked against an independent numpy / scipy implementation: the exact p-values agree to 12 digits (REML and DL, with and without Knapp-Hartung); the random p-value agrees with 20000 independent permutations.

### New - model comparison table (AIC / BIC / R^2)
- Section 6 **Model comparison table**: all subsets of the selected covariates / factors (up to 6 terms; with more terms only null, single terms, full and drop-one models) are fitted to the same studies (complete cases of all terms) and ranked by AICc: p, tau^2, I^2, R^2, logLik, AIC, AICc, BIC, dAICc, dBIC, Akaike weights and the likelihood-ratio test against the intercept-only model. A second table gives the likelihood-ratio test for dropping each term from the full model (df = number of dummy variables of a factor).
- logLik / AIC / AICc / BIC come from maximum-likelihood fits, because the restricted likelihood of REML cannot be compared between models with different fixed effects; tau^2, I^2 and R^2 use the selected tau^2 estimator, R^2 as in metafor (truncated at 0).

### Fix - fit statistics of the meta-regression output follow metafor
- logLik, deviance, AIC, BIC and AICc of the single meta-regression model were the ordinary (ML-type) likelihood evaluated at the REML tau^2. They are now defined as in `metafor::rma.uni`: the restricted likelihood for method REML (deviance = -2 logLik, BIC and AICc with k - p), the ordinary likelihood for all other estimators (deviance relative to the saturated model), AICc with max(k, npar + 2). For the BCG data with `~ ablat` (REML) the output is logLik -8.0873, deviance 16.1746, AIC 22.1746, BIC 23.3683, AICc 25.6032. Estimates, tests, tau^2, I^2 and R^2 are unchanged.

### New - GOSH plot (heterogeneity diagnostics)
- Section 10 **GOSH plot** (Olkin, Dahabreh and Trikalinos, 2012; `metafor::gosh`): random-effects model (inverse variance, selected tau^2 estimator) on all subsets of at least 2 studies (when there are at most 18 studies and fewer subsets than the chosen number) or on random subsets (spinner, seed field). Pooled estimate (back-transformed, log axis for ratios) against I^2 (Q-based or tau^2-based as chosen for the main analysis), with marginal densities and dashed lines for all studies.
- Large clouds are drawn as a density map (square bins, sequential blue ramp) so SVG / PDF exports stay small; the option **Colour subsets by the most influential study** switches to points coloured by inclusion of the study whose inclusion changes the mean I^2 most.
- Text output: quantiles of the estimate, I^2, tau^2 and number of studies over the subsets, the share of subsets with I^2 >= 50%, and per study the mean estimate and I^2 of the subsets that include / exclude it.

### New - orchard and caterpillar plots for subgroups
- Section 5 **Orchard plot** (Nakagawa et al., 2021): per subgroup the study estimates as semi-transparent dots (area proportional to precision), the pooled estimate (circle), its confidence interval (thick bar) and its prediction interval (thin line); k per subgroup.
- **Caterpillar plot**: per subgroup the studies ordered by effect with their confidence intervals and precision-sized squares, the pooled diamond and the prediction interval, with the numbers on the right.
- Both use the random-effects results (common-effect when only that model is chosen), the back-transformation and null line of the forest plot, and export as PNG / TIFF / JPG / SVG / PDF.

### New - clear data validation messages
- Before every run the table is checked for the current column assignment (`core/Validate.java`). Problems that would make the analysis impossible or wrong are listed **all at once** in the results panel, with the column, the row and the study label: text in a numeric column (`row 3 (Smith 2010) has "abc"`), events greater than the total, negative events, totals or person-time that are zero or negative, negative SDs, correlations outside [-1, 1] (or n <= 3 for Fisher's z), non-positive standard errors, lower limit above upper limit, and ratio limits that are not positive. The analysis is not run until they are fixed.
- Warnings do not stop the analysis; they appear in a "Data Check" section above the results: duplicate study labels (a repeated study would be counted twice), identical data in several rows, one column assigned to several parameters, missing values (those studies are left out), SD of zero, estimate outside its own confidence interval, a comma read as a decimal separator in values like `1,234`, non-numeric covariates.
- Row numbers count the data rows below the header line. Unused optional columns (CI limits when the standard error is given) are not checked.

### New - publication bias: PET-PEESE, selection model, fail-safe N (section 9)
- **PET-PEESE** (Stanley & Doucouliagos, 2014): weighted regression of the effect on SE (PET) and SE^2 (PEESE), intercept = bias-corrected effect with t-based CI, the conditional estimate (PEESE if the PET intercept differs from the null at alpha = 0.05, otherwise PET), the PET slope test, and a funnel plot with the PET line and the PEESE curve.
- **Selection model**: Vevea-Hedges three-parameter model (one cut at one-sided p = .025, relative publication probability delta for the other results; as `metafor::selmodel(type = "stepfun")`), maximum likelihood, adjusted effect with profile-likelihood CI, tau^2, delta, and the likelihood ratio test of selection. The favoured direction is Auto (sign of the pooled effect), Positive or Negative.
- **Fail-safe N**: Rosenthal (1979) with the 5k + 10 tolerance level, and Rosenberg (2005).
- All three appear in the results panel, the .xlsx and the .docx exports, and are saved with the project. Values were checked against an independent numpy/scipy implementation (27 new self-test checks).

### Fix - L'Abbe symbol sizes follow metafor::labbe for every pooling method
- The circle sizes no longer depend on the pooling method. For Inverse, MH, Peto, SSW, GLMM and LRP alike, `psize = sqrt(1/vi)` with `vi` the sum of the two arm-level sampling variances (`escalc` measures PLN for RR, PLO for OR, PR for RD, PAS for ASD, with the continuity correction of the analysis), rescaled linearly to `plim = c(0.5, 3.5)`; all 1 when every value is equal. On screen the diameter is 4 x psize (largest circle 14 px, smallest 2 px). The weight-based sizes (and the note about GLMM/LRP) are gone. Covered by new self-tests that measure the drawn circles.

### New - Load project / Save project
- Two buttons under the title bar. **Save project** writes the session to a `.json` file: the data table, analysis type, column assignments and every setting (all combo boxes, check boxes, fields, spinners and the covariate / factor selections; found by reflection, so new settings are saved automatically). **Load project** restores it and re-runs the analysis. The data is stored in the file, so a project opens even when the original spreadsheet has moved.

### New - Excel and Word export (section 11)
- **Save tables (.xlsx)**: one worksheet per analysis section, with the printed tables as real cells (numbers are numeric, keeping the printed decimals; confidence intervals are split into lower / upper columns) and the lines printed around them. Text sections without tables go on a "Notes" sheet.
- **Save full report (.docx)**: the whole output in the order of the results panel: headings, paragraphs, tables and figures (figures at the chosen dpi, at most 300). Written without external libraries.

### New - progress bar under the header
- A thin (4 px) bar in a lighter blue spans the window under the header; below it "Running analysis..." with a spinner and the percentage. The line appears only if a run lasts more than 0.25 s, so quick automatic updates do not shift the layout. The small bar at the bottom left is kept. Long sections (leave-one-out, GLMM, influence) report progress while they run.

### Fix - L'Abbe plot symbol sizes with GLMM and LRP
- With `method = "GLMM"` or `"LRP"` the circle sizes now follow `metafor::labbe`: `psize` is `sqrt(1/vi)` with `vi` the sum of the two arm-level sampling variances (`escalc` measures PLN for RR, PLO for OR, PR for RD, PAS for ASD, with the continuity correction of the analysis), rescaled linearly to the range 0.5-3.5 (all equal when every `wi` is equal). Previously the inverse variance of the study estimate was used and scaled to the largest study. The other methods still follow the study weights. Covered by two new self-tests.

### Fix - L'Abbe plot with GLMM and LRP
- With `method = "GLMM"` or `"LRP"` the study circles were missing (only the diagonal and the pooled lines were drawn) and the axes were fitted to nothing. These methods have no study weights, and the plot skipped every study without a weight. The symbol area now follows the inverse variance of the study estimate when the method gives no weights, and a note says so. The other methods are unchanged. Covered by a self-test for IV, Peto, MH, SSW, GLMM and LRP.

### Export - TIFF and JPG
- **Plot format** now offers PNG, **TIFF**, **JPG**, SVG and PDF, both for *Save all plots* and in the type list of *Save this plot* (`.tif` / `.jpg`; typed `.tiff` / `.jpeg` are accepted).
- **Plot resolution (dpi)** applies to PNG, TIFF and JPG (greyed out for SVG/PDF). The pixel size follows the dpi (96 dpi = 1x) and the resolution is recorded in the file (PNG pHYs, TIFF XResolution, JPEG JFIF density), so journals and Word/Photoshop read the right dpi.
- TIFF is lossless (LZW compression, ~3x larger than PNG at most); JPG uses quality 0.95 (lossy, not advised for line art). TIFF needs Java 9 or later (the build targets Java 11).

### Export - SVG and PDF next to PNG, at the chosen resolution
- New **Plot format** selector (PNG / SVG / PDF) in the Export section; it applies to *Save all plots* and is the default of *Save this plot* (the save dialog also offers the three types, and the file extension wins).
- SVG and PDF are produced by the very same drawing code as the PNG (the plots draw into a recording `Graphics2D`, `VectorGraphics`), so layout, line widths, dashes, grey levels and text positions are identical, for every plot including the forest plots.
- **Resolution.** *Plot resolution (dpi)* applies to PNG only (the spinner is disabled for SVG/PDF). Vector files are resolution independent and are written at the plot's natural size (scale 1: e.g. 620 x 520 px for the Doi plot; PDF 1 px = 0.75 pt, i.e. 465 x 390 pt), so a high dpi setting no longer produces huge pages (Acrobat refuses pages above 14 400 pt).
- SVG keeps live text (`<text>`, with `textLength` so the layout survives font substitution); PDF writes text as glyph outlines (no font embedding, same look everywhere, not selectable). Pages are white, as in the PNG.
- Both carry the plot title: `<title>` in the SVG, document information (Title, Creator, Producer) in the PDF.
- Checked by rasterising the PDFs and rendering the SVGs in Chromium, then comparing with the PNG (L'Abbe, Doi, Baujat for RR, OR, RD, ASD: mean grey difference below 1 of 255).

### Publication bias - Doi plot and LFK index
- New option **Doi plot and LFK index**: |Z-score| (reversed axis) against the effect size, studies joined in order of effect, LFK index printed in the plot, optional dashed line through the apex (smallest |Z|; checkbox **Mark the apex**, off by default because `metasens::doiplot` does not draw it), plus a table (effect, SE, Z, |Z|) and the interpretation (|LFK| <= 1 no, <= 2 minor, > 2 major asymmetry).
- The index follows `metasens::lfkindex` exactly (precision-weighted mid-ranks, N = 100 max(var) / var, percentile = (MidRank - 0.5) / sum(N), index = 5 / (2k) * sum(Z + (maxZ - minZ) / (max - min of TE - TE.apex) * (TE - TE.apex))). Ratio measures are on the log scale with back-transformed axis labels. Fewer than 5 studies: a caution is printed.

### Diagnostics - L'Abbe plot
- New option **L'Abbe plot (binary outcomes)** (metabin): event rate in the experimental group against the control group, symbol area proportional to the weight (common-effect weights if shown, otherwise random-effects), grey no-effect diagonal, dashed lines for the pooled common-effect and random-effects estimates (RR, OR, RD, ASD), study labels optional. Both axes use the same limits, fitted to the data.

### Fix - "Save this plot" type and extension
- Choosing PNG / SVG / PDF in the save dialog now switches the extension of the file name, and the chosen type is what gets written.
- **Bug:** with *Plot format* = PNG, choosing PDF (or SVG) in the dialog could leave the name as `.png`, and the file was then written as PNG. `JFileChooser.setFileFilter` clears the selected file, and some look-and-feels / platforms (seen on Windows with Nimbus) refresh the name field only after the filter event, overwriting the new name.
- **Fix:** new `PlotChooser`. The name is refreshed after the dialog's own update (`invokeLater`), and, independently of what the field shows, the extension is fixed when *Save* is pressed: the selected type decides, unless the user typed another known extension (`fig.svg`). The dialog also asks before replacing an existing file. Covered by self-tests.

### Fix - x-axis labels on log-scale plots with a narrow effect range
- When the 1-2-5 log ticks left fewer than two labels inside the axis (e.g. risk ratios between 0.5 and 0.7), the axis showed none; it now falls back to evenly spaced labels. Affects funnel, Doi and any other plot using the shared axes.

### Forest plots - "Experimental" / "Control" group headings (metabin, metacont, metainc)
- The column headings of two-group outcomes now carry the group labels above them, centred over each group's columns (Events/Total, Total/Mean/SD, Events/Time), as in meta::forest. Applies to the main and subgroup forest plots; the labels are `labelE` / `labelC` of the settings.

### GLMM - tau^2-based I^2 (metafor) shown next to the Q-based I^2 (meta)
- meta and MetaJava derive I^2 and H from the Wald-type Q ((Q - df) / Q); `metafor::rma.glmm` reports I^2 = tau^2 / (tau^2 + vt) with the typical sampling variance vt of the continuity-corrected study estimates (H^2 = 1 / (1 - I^2)). The two definitions differ, so the summary now prints both for random-effects GLMM analyses (all models), e.g. CM.EL, 20 studies: I^2 = 76.7% [64.2%; 84.8%], H = 2.07 [1.67; 2.56] (Q-based) and I^2 = 75.2%, H = 2.01 (tau^2-based, metafor: 75.249%, H^2 = 4.04). The methods block names both definitions. The forest plot keeps the Q-based I^2 (as meta::forest).
- CM.EL Wald-type Q on the 20-study data set: MetaJava 81.44 is the exact value (independent check: 81.4358); metafor / meta print 81.56 because the saturated model is optimised numerically. The likelihood-ratio Q agrees to all printed digits (81.6036). Documented in the self-test as for UM.RS.

### Fix - GLMM UM.RS Wald-type heterogeneity test now equals metafor / meta
- **Problem.** For `model.glmm = "UM.RS"` the Wald-type Q was Cochran's Q on the study log odds (rate) ratios (80-study data set: Q = 325.91, I^2 = 75.8%, H = 2.03), whereas `metafor::rma.glmm` and `meta::metabin` report 296.21 (I^2 = 73.3% [66.8%; 78.6%], H = 1.94 [1.74; 2.16]). Cochran's Q is the correct Wald statistic only for UM.FS.
- **Cause.** metafor computes the UM.RS statistic as the Wald test, on the saturated model (study-specific group effects, common intercept, random study intercepts), that the study-specific effects are equal, with the covariance matrix lme4 reports for its fixed effects. That covariance accounts for the random study intercepts and for the correlation between the study effects through the shared intercept.
- **Fix.** The saturated UM.RS fit now returns its parameters (intercept, sigma, study effects and the modes of the random intercepts); the statistic is computed from the information of the fixed effects with the random intercepts integrated out around their modes (closed form, no extra optimisation). The saturated fit is shared with the likelihood-ratio test, so a full UM.RS fit costs the same as before.
- **Agreement.** 80-study data set: Q = 296.23 against 296.2068 in metafor; the remaining 0.02 is because lme4 stops a little short of the exact saturated-model sigma (Q changes by about 290 per unit of sigma), I^2, H and their CIs agree to the printed digits.
- **Leave-one-out and cumulative** tables now show the UM.RS I^2 derived from this statistic and therefore need the saturated fit (about 20% longer on the 80-study data set). The Baujat plot reads only the common-effect estimate and skips it (`MetaSpec.skipHet`).
- UM.FS, CM.EL, CM.AL, proportions and rates are unchanged.

### Forest plots - compact text below the plot (as meta::forest)
- The heterogeneity line, the tests and the "Details of meta-analysis methods" block now start directly under the last row (e.g. "Prediction interval") instead of always below the x-axis, removing the large vertical gap on the left.
- Each line is checked against the extent of the x-axis, its tick labels and the optional "favours" labels: a line that would overlap them (long lines, wide tick labels such as -0.6 -0.4 ... for correlations, or hazard-ratio axes) is moved below the axis, and so are the lines after it.
- When the heterogeneity / test lines are only slightly too long to fit to the left of the axis (typical for correlations and proportions, whose tick labels start close to the left columns), the plot and the right-hand columns are shifted right by the missing amount (at most 140 px) instead of moving the whole footer below the axis.
- Applies to the main, subgroup, leave-one-out and cumulative forest plots (shared renderer).

### Forest plots - study squares and CI lines now look like meta::forest
- The confidence-interval line and the vertical estimate tick are no longer hidden by the gray square: they are drawn in solid black over it.
- When the whole confidence interval fits inside the square, the horizontal line and the vertical tick are drawn in white (previously nothing was visible).
- Applies to the main, subgroup, leave-one-out and cumulative forest plots (they share one renderer).

### Performance - parallel workflows and background analysis
- New shared worker pool (`Par`): 4 threads by default, capped at the number of cores. Change it with the **CPU threads** spinner in the header (remembered between sessions) or `-Dmetajava.threads=N`.
- Analysis no longer runs on the UI thread. Independent sections (summary/forest, subgroup, regression, leave-one-out, cumulative, funnel, bias test, trim-and-fill, influence, Baujat, radial) are computed in the background, plots are rendered there too, and a busy bar shows progress. A run that has been superseded by a newer setting change is dropped. Saving plots no longer blocks the window.
- Parallelised: leave-one-out, cumulative, influence diagnostics (one leave-one-out refit per study), the NNF prediction-interval bootstrap, the Jackson/BJ matrix construction, and the GLMM per-study likelihood terms.
- Eigenvalues (Jackson/BJ tau^2 confidence interval) now use Householder tridiagonalisation + implicit QL instead of cyclic Jacobi (agrees to ~1e-12, several times faster, more so for large k).
- Leave-one-out and cumulative skip the tau^2 confidence interval (never displayed there) unless I^2 is derived from tau^2.
- NNF bootstrap: the seeded random stream (which depends only on k) is cached; replicates are evaluated in parallel. Results are bit-identical to the single-threaded version.
- Hat values in the influence diagnostics use only the diagonal of the hat matrix (was a full k x k product).
- Results are identical to the previous build irrespective of the number of threads (floating-point summation order is unchanged).

### Performance - UM.RS leave-one-out, cumulative meta-analysis and Baujat plot (80-study data set no longer appears frozen)
- **Cause.** 93% of a UM.RS fit was spent on the saturated model behind the likelihood-ratio heterogeneity test (a Brent search for every study at every Nelder-Mead step, serial), which was computed again in the random-effects fit and in every leave-one-out, cumulative and Baujat refit although none of them displays it. The Baujat plot also ran its refits serially, with the random-effects fit, and again each time it was rendered or saved.
- **Saturated model.** The study effects are now maximised by Newton steps on the analytic score (the routine used for UM.FS, generalised to a profiled study effect), in parallel over studies, started from the previous step; the saturated log-likelihood is computed once and shared by the common-effect and random-effects fits. Boundary maxima (studies with a zero cell) stay confined to the same +/- 15 interval as before.
- **Leave-one-out, cumulative, Baujat.** They skip the likelihood-ratio test (not displayed there); Baujat refits only the common-effect model, in parallel, and keeps its points so that saving the plot does not repeat the work.
- **Progress bar.** It is indeterminate until the first section has finished (a long first section no longer shows a bar stuck at 0%).
- **Results.** Estimates, SEs, tau^2, sigma^2, log-likelihoods and both Q statistics are unchanged (identical to 9 decimals on the regression matrix and on 45 extreme random data sets with zero cells and double-zero studies; at most 5e-15 relative in the likelihood-ratio Q). The Baujat image is identical.
- **Timings (one thread).** Main analysis of the 80-study data set: 13.6 s -> 0.7 s. 20 studies: main analysis 4.5 s -> 0.7 s, leave-one-out 37.2 s -> 4.8 s, cumulative 16.4 s -> 2.1 s, Baujat 36.3 s -> 1.3 s (saving it again: 36.9 s -> 0 s). 80 studies after the change: leave-one-out 97 s, cumulative 39 s, Baujat 34 s on one thread (they scale with the number of worker threads).

### Performance - GLMM (UM.RS, UM.FS, CM.EL, and every binomial model)
- **CM.EL:** the non-central hypergeometric pmf no longer sums all min(n1, n2) + 1 terms with one `exp` each at every evaluation. The maximum is located by bisection (the log-weights are concave) and the weights are generated outwards from it by the exact ratio recurrence, stopping at 3e-20 of the maximum. Cost now grows with the standard deviation instead of with the sample size: k = 40 studies of about 3000 patients, 30.6 s -> 0.32 s on one thread.
- **Binomial arms (UM.RS, UM.FS, CM.AL):** the log binomial coefficient (three `lgamma` calls) is computed once per arm instead of at every evaluation, one `exp` serves both the probability and log(1 + exp(eta)), and `Math.log1p` (a slow software routine) is replaced by an equivalent through `Math.log`. UM.RS, k = 150: 8.0 s -> 1.7 s on one thread.
- **UM.FS (fixed study effects):** the study intercept is no longer found by a Brent search (about 40 evaluations of the marginal likelihood per study and per likelihood evaluation, restarted from scratch every time). It is now maximised by safeguarded Newton steps on the analytic score of the marginal log-likelihood, which differentiates the mode of the random effect, the Laplace scale and every adaptive Gauss-Hermite node (so it is the exact derivative of the likelihood that is maximised, for any nAGQ, and Laplace included). The curvature comes from the secant of the last two scores once available, and the search is bracketed by the sign of the score. Each search starts from the optimum (and the random-effect mode) that the same study had at the previous Nelder-Mead evaluation. On average 1.2 to 3 evaluations per search (the warm start often leaves nothing to do) instead of about 40; the Brent search remains as a fallback if a step is not finite or does not converge (it was never needed in testing). Binomial and Poisson (IRR) arms are both covered. Benchmarks on one thread: 30 UM.FS fits (OR and IRR, k = 8 to 50, nAGQ = 1, 3, 7, sparse data) 5.0 s -> about 0.65 s; 300 realistic datasets (k = 5 to 30, tau 0 to 1, rare events, nAGQ = 1 and 7) 78.7 s -> 9.1 s; 150 random extreme datasets 222 s -> 16.6 s.
- On 300 realistic datasets the log-likelihoods, Q statistics and NaN pattern are identical to the previous build, and the parameters agree to a median of 5e-9 (maximum 9e-7, for the standard error of a random-effects fit close to tau^2 = 0), which is the size of the change the previous build itself shows when only the starting value is changed. Only data with complete separation (an effect running to minus or plus infinity, e.g. all events in one arm) give start-dependent estimates and standard errors, in both builds. The self-test now includes UM.FS regression checks (OR with nAGQ = 7 and 1, IRR; common-effect and random-effects fits).

### Performance - parallel pool refinements
- `Par` now uses a work-stealing `ForkJoinPool`: a loop started inside a pool task (e.g. the GLMM likelihood inside a leave-one-out refit that runs in the pool) is split further and the waiting thread helps, so nested loops use idle threads and cannot deadlock. Previously they ran sequentially.
- Cancellation now reaches inside the parallel loops: `Par.withCancel(...)` attaches the "superseded" condition to the analysis; loops and submitted tasks stop at the next iteration instead of finishing a long GLMM fit.
- `Glmm.gh()` (Gauss-Hermite nodes, read for every study at every likelihood evaluation) is lock-free (`ConcurrentHashMap`), so the threads no longer queue on a lock.
- `Par.threads` is `volatile` instead of read through a `synchronized` getter on every loop.
- Results are unchanged (checked byte for byte on 19 analysis configurations with 4 threads).

### Performance - tau² confidence interval of DerSimonian-Laird (Jackson / generalised-Q)
* The Jackson interval (default CI of tau² with DL) took seconds for k = 3-5 and was slow for larger k. The distribution of the
  generalised Q now uses the Ruben/Farebrother series (the algorithm of `CompQuadForm::farebrother` called by metafor), with Imhof's
  integral as fallback; the eigenproblem is reduced to (k-p)x(k-p) once; the root search needs fewer evaluations and runs both
  bounds concurrently; results are cached; subgroups are pooled in parallel. k = 3: 5 s -> under 1 ms. Results agree with the
  previous version to about 1e-9 (the old integral was truncated for k <= 4).

### Fixed - influence diagnostics now follow metafor 5.2-1 (`influence.rma.uni`, `plot.infl.rma.uni`)
- Influential-study rule (red points / `*`): `|rstudent| > 1.96` is no longer a criterion. Only `|dffits| > 3*sqrt(p/(k-p))`, `pchisq(cook.d, p) > 0.5`, `hat > 3p/k` and any `|dfbs| > 1` flag a study. `rstudent` and `cov.r` are displayed only.
- DFBETAS: the denominator is now `sqrt(s2w * diag(vb.del))`, where `vb.del` is the full-design covariance matrix with `tau2.del` plugged in (all k studies), instead of the leave-one-out model's `vb`.
- Knapp-Hartung (`knha`/`adhoc`) regression models: `s2w` scaling is applied to rstudent, dffits, DFBETAS, Cook's distance and cov.r.
- Studies whose leave-one-out fit fails now show NA (NaN) rather than 0, and are skipped in the plot.
- Common-effect (FE) models are no longer silently refitted with DL for the diagnostics.
- DFFITS threshold no longer clamps `k-p` to 1.
- Plot: added the dotted reference line at `qchisq(0.5, p)` (cook.d) and `k-p` (QE.del); cov.r uses a log y-axis (metafor `logcov=TRUE`).

## 0.1.0

First public release of MetaJava (stand-alone Java edition of the analyses of the R packages meta and metafor).

* **Forest plots** (main, subgroup, leave-one-out, cumulative): the x-axis is centred on the null value
  (1 for ratio measures, 0 for differences and correlations), as in `meta::forest`; single means, proportions and
  rates keep an uncentred axis.
* **Influence diagnostics:** studies flagged as influential/outliers are drawn as red points, as in metafor.
* **Prediction intervals:**
  * *Subgroup prediction interval* now controls the subgroup rows of the subgroup forest plot, independently of the main
    *Prediction interval* option;
  * the prediction interval of the main analysis is printed below the test of heterogeneity;
  * the *Results for subgroups* table has a prediction-interval column on the right;
  * new **PI confidence level** setting (default 95): the chosen level appears in the text output (*Prediction interval (99%)*,
    *99% PI* columns) and in the forest plots.
* **Subgroup analysis section:** new options *Test for effect in subgroup* and *Details of meta-analysis methods*
  (subgroup forest plot); *Subgroup variable* moved here.
* **Meta-regression section:** *Covariates* and *Factors* moved here, with an exclusive *none* entry.
* **Build:** the JAR is compiled for Java 11 (class-file version 55), so that it runs on the Java 11+ stated in the
  README (the previous pre-release JAR had been compiled with Java 21). Maven-style source layout, `build.sh` /
  `build.bat` with a headless `SelfTest`, `pom.xml`, GitHub Actions `check` (Java 11/17/21 on Linux, Windows, macOS)
  and `release` (attaches the JAR to a GitHub Release).

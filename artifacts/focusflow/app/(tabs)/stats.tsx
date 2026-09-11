/**
 * Stats is the single analytics entry point.
 *
 * The former screen contained the superseded detailed yesterday report,
 * lifetime/all-time totals, and a second temptation-log pipeline. Keeping this
 * route as a thin entry point prevents those obsolete paths from being
 * reintroduced alongside the rule-based Stats & Insights experience.
 */
export { StatsInsightsExperience as default } from '@/components/StatsInsightsExperience';
/**
 * Compatibility route for older notification/settings links.
 *
 * Reports used to open the superseded detailed yesterday screen. All report
 * content now lives in the Stats route, which owns the Yesterday view.
 */
import React from 'react';
import { Redirect } from 'expo-router';

export default function ReportsRedirect() {
  return <Redirect href="/(tabs)/stats" />;
}
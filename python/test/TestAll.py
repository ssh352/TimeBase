import unittest
import datetime
import sys, os

import HTMLTestRunner as HTMLTestRunner

testmodules = [
    'TestTickDB',
    'TestStream',
    'TestLoader',
    'TestCursor',
    'TestPackageHeader',
    'TestQQL',
    'TestMemoryManagement'
    ]

suite = unittest.TestSuite()

for t in testmodules:
    try:
        # If the module defines a suite() function, call it to get the suite.
        mod = __import__(t, globals(), locals(), ['suite'])
        suitefn = getattr(mod, 'suite')
        suite.addTest(suitefn())
    except (ImportError, AttributeError):
        # else, just load all the test cases from the module.
        suite.addTest(unittest.defaultTestLoader.loadTestsFromName(t))

reportsDir = 'reports'
if not os.path.exists(reportsDir):
    os.makedirs(reportsDir)
reportFile = reportsDir + '/dxapi-test-report-' + datetime.datetime.now().strftime('%d%m%Y-%H%M%S') + '.html'
fileStream = open(reportFile, 'w')
title = 'Dxapi test report (Python version: ' + ('3' if sys.version.startswith("3.") else '2') + ')'
runner = HTMLTestRunner.HTMLTestRunner(stream = fileStream, title = title)
result = runner.run(suite)

print('Tests finished')
print('Errors: ' + str(result.error_count) + ", Failures: " + str(result.failure_count) + ", Tests OK: " + str(result.success_count))
print('See reports in "' + reportFile + '"')

if result.error_count > 0 or result.failure_count > 0:
    sys.exit(-1)
else:
    sys.exit(0)

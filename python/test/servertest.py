import unittest

import sys, os, inspect, struct
from subprocess import Popen
import socket

import copy
import time

print("Python version: " + str(sys.version))

py36 = sys.version.startswith("3.")
if py36:
    import urllib.request as urllib2
else:
    import urllib2

platform = ("linux" if sys.platform.startswith('linux') else "windows")

path = os.path.join(os.getcwd(), "..", "dxapi", platform, ("py36" if py36 else "py27"), ("x64" if struct.calcsize('P') * 8 == 64 else "x86"))
sys.path.append(path)

import testutils
import dxapi

class TBServerTest(unittest.TestCase):

    HOST = os.getenv('TIMEBASE_HOST', 'localhost')
    PORT = os.getenv('TIMEBASE_PORT', 8131)
    DELTIX_HOME = os.getenv('DELTIX_HOME', None)

    TIMEBASE_WAIT_TIMEOUT = 10

    @classmethod
    def setUpClass(cls):
        print('TimeBase host: ' + str(cls.HOST))
        print('TimeBase port: ' + str(cls.PORT))
        print('TimeBase home: ' + str(cls.DELTIX_HOME))

        if not cls.DELTIX_HOME == None:
            print('Starting TimeBase...')
            socket.setdefaulttimeout(5)
            cls.server = Popen([cls.getTimeBaseCommand(), "-port", str(cls.PORT)])
            cls.waitUntilStarted(cls.TIMEBASE_WAIT_TIMEOUT)
            print('OK')

    @classmethod
    def tearDownClass(cls):
        if not cls.DELTIX_HOME == None:
            try:
                print('Shutdown TimeBase...')
                s = urllib2.urlopen(cls.httpURL() + "/shutdown...").read()
                time.sleep(1) # temp fix wait a little for closing server
                if (cls.server != None):
                    cls.server.terminate()
                print('OK')
            except:
                cls.printException()
                pass

    def setUp(self):
        url = self.dxtickURL()
        print('Connecting to ' + str(url) + "...")
        self.db = dxapi.TickDb.createFromUrl(url)
        self.db.open(False)
        print('OK')

    def tearDown(self):
        self.db.close()
        
    @classmethod
    def getTimeBaseCommand(cls):
        if cls.isLinux():
            print('Linux OS')
            return './timebase'
        else:
            print('Windows OS')
            return 'timebase.cmd'
    
    @classmethod
    def isLinux(cls):
        if sys.platform.startswith('linux'):
            return True
        else:
            return False

    # helpers

    @classmethod
    def waitUntilStarted(cls, waitTimeout):
        while waitTimeout > 0:
            try:
                time.sleep(1)
                waitTimeout = waitTimeout - 1
                response = urllib2.urlopen(cls.httpURL())
                response.read()
                return
            except:
                pass

    @classmethod
    def dxtickURL(cls):
        return "dxtick://" + cls.HOST + ":" + str(cls.PORT)

    @classmethod
    def httpURL(cls):
        return "http://" + cls.HOST + ":" + str(cls.PORT)

    def createStream(self, key, polymorphic = False):
        with open('testdata/' + key + '.xml', 'r') as schemaFile:
            schema = schemaFile.read()
        options = dxapi.StreamOptions()
        options.polymorphic = polymorphic
        options.metadata(schema)

        return self.db.createStream(key, options)

    def createStreamQQL(self, key):
        with open('testdata/' + key + '.qql', 'r') as qqlFile:
            qql = qqlFile.read()

        cursor = self.db.executeQuery(qql)
        self.assertTrue(cursor.next())
        reportMessage = cursor.getMessage()
        self.assertEqual(reportMessage.instrumentType, 'SYSTEM')
        self.assertEqual(reportMessage.messageText, 'Stream created')

        return self.db.getStream(key)

    def deleteStream(self, key):
        stream = self.db.getStream(key)
        if stream != None:
            stream.deleteStream()


class TestWithStreams(TBServerTest):

    streamKeys = [
        'bars1min', 'tradeBBO', 'l2'
    ]

    entities = {
        'AAPL':dxapi.InstrumentIdentity(dxapi.InstrumentType.EQUITY, 'AAPL'),
        'GOOG':dxapi.InstrumentIdentity(dxapi.InstrumentType.EQUITY, 'GOOG'),
        'IBM':dxapi.InstrumentIdentity(dxapi.InstrumentType.EQUITY, 'IBM'),
    }

    types = {
        'trade':'deltix.timebase.api.messages.TradeMessage',
        'bbo':'deltix.timebase.api.messages.BestBidOfferMessage',
        'bar':'deltix.timebase.api.messages.BarMessage',
        'l2':'deltix.timebase.api.messages.L2Message'
    }

    def setUp(self):
        TBServerTest.setUp(self)
        for key in self.streamKeys:
            self.createStreamQQL(key)
        self.assertEqual(len(self.db.listStreams()), len(self.streamKeys) + 1)

        stream = self.db.getStream(self.streamKeys[0])
        testutils.loadBars(stream, 10000, 0, 1000000000, list(self.entities.keys()))

        stream = self.db.getStream(self.streamKeys[1])
        testutils.loadTradeBBO(stream, 5000, 0, 1000000000, list(self.entities.keys()))

        stream = self.db.getStream(self.streamKeys[2])
        testutils.loadL2(stream, 10000, 10, 0, 1000000000, list(self.entities.keys()))

    def tearDown(self):
        for key in self.streamKeys:
            self.deleteStream(key)
        self.assertEqual(len(self.db.listStreams()), 1)
        TBServerTest.tearDown(self)

    # utils

    def read(self, cursor, count):
        messages = []
        for i in range(count):
            self.assertTrue(cursor.next())
            messages.append(copy.deepcopy(cursor.getMessage()))
        return messages

    def readMessages(self, stream, _from, to):
        options = dxapi.SelectionOptions()
        options._from = _from
        options.to = to
        cursor = stream.createCursor(options)
        messages = []
        try:
            while cursor.next():
                messages.append(copy.deepcopy(cursor.getMessage()))
            return messages
        finally:
            cursor.close()

    def readCount(self, cursor):
        readCount = 0
        while cursor.next():
            readCount = readCount + 1
        return readCount

    def checkCursorTypes(self, cursor, types):
        messages = self.read(cursor, 20)
        self.checkTypes(messages, types)

    def checkCursorSymbols(self, cursor, symbols):
        messages = self.read(cursor, 20)
        self.checkSymbols(messages, symbols)

    def checkCursorTypesAndSymbols(self, cursor, types, symbols):
        messages = self.read(cursor, 20)
        self.checkTypes(messages, types)
        self.checkSymbols(messages, symbols)

    def checkTypes(self, messages, types):
        msgSet = set()
        for message in messages:
            msgSet.add(message.typeName)
        self.assertEqual(msgSet, types)

    def checkSymbols(self, messages, symbols):
        smbSet = set()
        for message in messages:
            smbSet.add(message.symbol)
        self.assertEqual(smbSet, symbols)

    def compareEntities(self, entities, symbols):
        symbolsSet = set()
        for entity in entities:
            symbolsSet.add(entity.symbol)
        self.assertEqual(symbolsSet, symbols)

    def removeAll(self, collection, elements):
        for element in elements:
            collection.remove(element)

    def addAll(self, collection, elements):
        for element in elements:
            collection.add(element)

    def msToNs(self, ms):
        return ms * 1000000


if __name__ == '__main__':
    unittest.main()

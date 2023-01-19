require("class")

---@type Socket
local socket

socket.Thread.execute(1)
socket.Thread.execute(<error descr="Type mismatch. Required: 'number' Found: '\"invalid\"'">"invalid"</error>)
socket.AnonymousThread.execute(1)
socket.AnonymousThread.execute(<error descr="Type mismatch. Required: 'number' Found: '\"invalid\"'">"invalid"</error>)

---@type IndexerExtendedClass
local probablyDontDoThis

probablyDontDoThis.foo(1)
probablyDontDoThis.foo(<error descr="Type mismatch. Required: 'number' Found: '\"invalid\"'">"invalid"</error>)
probablyDontDoThis.bar(1)
probablyDontDoThis.bar(<error descr="Type mismatch. Required: 'number' Found: '\"invalid\"'">"invalid"</error>)

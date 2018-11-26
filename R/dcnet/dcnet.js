var net = document.getElementById("network");

function labels_on()
{
  var nodes = net.getSelection();
  alert("DEBUG: nodes selected: "+nodes.length);
}

if ((typeof(document)) == 'undefined')
{
  alert("DEBUG: document undefined.");
}
else if ((document === null))
{
  alert("DEBUG: document is null.");
}
else
{
  alert("DEBUG: document: "+typeof(document));
}


// var bTest = document.getElementById("btest");
//bTest.onclick = function() {
//   alert("DEBUG: testing...");
// };
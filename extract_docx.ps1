$path = 'C:\Users\tk854\Music\Downloads\PocketPal_Competitor_Analysis.docx'
$tempDir = Join-Path $env:TEMP ([Guid]::NewGuid().ToString())
New-Item -ItemType Directory -Path $tempDir -Force | Out-Null
$zipPath = Join-Path $tempDir 'doc.zip'
Copy-Item $path $zipPath
Expand-Archive $zipPath -DestinationPath $tempDir
$xmlPath = Join-Path $tempDir 'word\document.xml'
if (Test-Path $xmlPath) {
    [xml]$xml = Get-Content $xmlPath -Raw
    $ns = New-Object System.Xml.XmlNamespaceManager($xml.NameTable)
    $ns.AddNamespace('w', 'http://schemas.openxmlformats.org/wordprocessingml/2006/main')
    $nodes = $xml.SelectNodes('//w:t', $ns)
    $content = $nodes | ForEach-Object { $_.InnerText } | Out-String
    $content
} else {
    Write-Error "Could not find word/document.xml in the docx file."
}
Remove-Item $tempDir -Recurse -Force

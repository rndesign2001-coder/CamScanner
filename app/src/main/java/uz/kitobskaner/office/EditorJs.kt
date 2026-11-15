package uz.kitobskaner.office

/**
 * Hujjat sahifasidagi tahrirlash dvigateli (WebView ichida ishlaydi).
 *
 * - DOCX: sahifalarga bo'lish ("kompyuter" ko'rinishi) yoki ekranga moslash ("mobil" ko'rinish),
 *   matnni joyida tahrirlash, formatlash (qalin, kursiv, rang, o'lcham, tekislash).
 * - PPTX: shakllardagi matnni tahrirlash, shakllarni surish/o'lchamini o'zgartirish/o'chirish,
 *   yangi matn qutisi; mobil ko'rinishda slaydlar matni (konspekt).
 * - XLSX: kataklarni tahrirlash; mobil ko'rinishda qatorlar kartochka ko'rinishida.
 *
 * [QO.collect] o'zgarishlarni JSON qilib beradi — [OfficeEditor] ularni asl faylga yozadi.
 * Har bir paragraf/run/shakl `data-p`/`data-r`/`data-s` identifikatoriga ega ("qism|teg|tartib").
 */
const val EDITOR_JS = """
var QO=(function(){
var K=document.body.getAttribute('data-kind')||'';
var editing=false,lay=false,snap=false,orig={},oalign={},pb={},base={},cells={},moved={},removed={},nb=0,sel=null,view='desktop',PG=null,drag=null;
function hex(c){if(!c)return null;var m=c.match(/[\d.]+/g);if(!m||m.length<3)return null;if(m.length>3&&parseFloat(m[3])<0.05)return null;
 var h='';for(var i=0;i<3;i++){var v=Math.min(255,Math.round(parseFloat(m[i]))).toString(16);h+=v.length<2?'0'+v:v}return h.toUpperCase()}
function isP(el){return el&&el.nodeType==1&&el.hasAttribute('data-p')}
function deco(el){var u=false,s=false;while(el&&el.nodeType==1){var d=getComputedStyle(el).textDecorationLine||'';
 if(d.indexOf('underline')>=0)u=true;if(d.indexOf('line-through')>=0)s=true;if(isP(el)||el.classList.contains('sp'))break;el=el.parentElement}return[u,s]}
function bgOf(el){while(el&&el.nodeType==1){if(isP(el)||el.classList.contains('sp'))return null;var b=hex(getComputedStyle(el).backgroundColor);if(b)return b;el=el.parentElement}return null}
function fam(cs){return(cs.fontFamily||'').split(',')[0].replace(/["']/g,'').trim()}
function props(el){var cs=getComputedStyle(el),d=deco(el);return{b:(parseInt(cs.fontWeight)||400)>=600,i:cs.fontStyle=='italic',u:d[0],s:d[1],
 c:hex(cs.color),sz:Math.round(parseFloat(cs.fontSize)*1.5)/2,bg:bgOf(el),ff:fam(cs)}}
function alignOf(el){var a=getComputedStyle(el).textAlign;if(a=='start'||a=='-webkit-left')return'left';if(a=='end'||a=='-webkit-right')return'right';if(a=='-webkit-center')return'center';return a}
function sig(el,id){return JSON.stringify(ser(el,id))}
function roots(){if(K=='docx')return document.querySelectorAll('.page');if(K=='pptx')return document.querySelectorAll('.sp[data-s],.sp[data-new]');
 if(K=='xlsx')return document.querySelectorAll('td[data-c]');return[]}
function snapshot(){if(snap)return;snap=true;
 var rs=document.querySelectorAll('[data-r]'),i;for(i=0;i<rs.length;i++){if(!rs[i].hasAttribute('data-obj'))base[rs[i].getAttribute('data-r')]=props(rs[i])}
 var ps=document.querySelectorAll('[data-p]');for(i=0;i<ps.length;i++){var id=ps[i].getAttribute('data-p');if(orig[id]!==undefined)continue;
  oalign[id]=alignOf(ps[i]);pb[id]=props(ps[i]);orig[id]=sig(ps[i],id)}
 var cs=document.querySelectorAll('td[data-c]');for(i=0;i<cs.length;i++)cells[cs[i].getAttribute('data-c')]=cs[i].innerText}
function setEditable(on){var r=roots();for(var i=0;i<r.length;i++){if(K=='pptx'&&!r[i].querySelector('[data-p]')){r[i].contentEditable='false';continue}
 r[i].contentEditable=on?'true':'false'}}
function edit(on){editing=on;if(on){snapshot();if(K!='docx'&&view=='mobile')setView('desktop')}lay=false;document.body.classList.remove('lay');pick(null);
 setEditable(on);document.body.classList.toggle('editing',on);
 try{document.execCommand('styleWithCSS',false,true);document.execCommand('defaultParagraphSeparator',false,'p')}catch(e){}
 if(!on&&document.activeElement)document.activeElement.blur()}
function layout(on){if(on){snapshot();if(view=='mobile')setView('desktop');lay=true;document.body.classList.add('lay');setEditable(false);
 if(document.activeElement)document.activeElement.blur()}else{lay=false;document.body.classList.remove('lay');pick(null);setEditable(editing)}}
function segs(n0,out,ctx){for(var n=n0.firstChild;n;n=n.nextSibling){
 if(n.nodeType==3){var t=n.data.replace(/ /g,' ').replace(/[\r\n]+/g,' ');if(!t)continue;var el=n.parentElement,pr=props(el),
   bs=(ctx.r&&base[ctx.r])||ctx.pb,d={t:t};if(ctx.r)d.r=ctx.r;for(var k in pr){if(!bs||pr[k]!==bs[k])d[k]=pr[k]}out.push(d)}
 else if(n.nodeType==1){if(n.hasAttribute('data-obj')){out.push({o:n.getAttribute('data-r')});continue}
  if(n.hasAttribute('data-newimg')){out.push({img:n.getAttribute('data-newimg'),w:+n.getAttribute('data-w'),h:+n.getAttribute('data-h')});continue}
  if(n.hasAttribute('data-newpb')){out.push({pb:1});continue}
  var cl=n.classList;if(cl.contains('bul')||cl.contains('pgno')||cl.contains('hdl'))continue;
  if(n.tagName=='BR'){out.push({br:1,r:ctx.r});continue}
  if(cl.contains('tab')){out.push({tab:1,r:ctx.r});continue}
  if(n.tagName=='IMG')continue;
  var sv=ctx.r;if(n.hasAttribute('data-r'))ctx.r=n.getAttribute('data-r');segs(n,out,ctx);ctx.r=sv}}}
function ser(el,id){var out=[];segs(el,out,{r:null,pb:pb[id]||null});if(out.length&&out[out.length-1].br)out.pop();
 var a=alignOf(el),p={rs:out};if(a!=oalign[id])p.a=a;
 var l=el.getAttribute('data-list');if(l)p.l=l;var ind=+(el.getAttribute('data-indd')||0);if(ind)p.ind=ind;
 var ls=el.getAttribute('data-ls');if(ls)p.ls=+ls;var sa=el.getAttribute('data-sa');if(sa)p.sa=+sa;return p}
function inEdit(el){return !!el.closest('[contenteditable="true"]')}
function collect(){var res={paras:[],cells:[],moved:moved,removed:Object.keys(removed),boxes:[]};if(!snap)return res;
 var groups={},last=null,all=document.querySelectorAll('[data-p],p,h1,h2,h3,h4,h5,h6'),i,j;
 for(i=0;i<all.length;i++){var el=all[i];if(el.closest('[data-new]')||el.closest('[data-newtbl]'))continue;var id=el.getAttribute('data-p');
  if(!id){if(!last||!inEdit(el)||(el.parentElement&&el.parentElement.closest('[data-p]'))||el.querySelector('[data-p]'))continue;id=last}else last=id;
  (groups[id]=groups[id]||[]).push(el)}
 for(var id2 in orig){var g=groups[id2]||[];if(g.length==1&&sig(g[0],id2)==orig[id2])continue;var ps=[];for(j=0;j<g.length;j++)ps.push(ser(g[j],id2));
  res.paras.push({id:id2,ps:ps})}
 for(var c in cells){var td=document.querySelector('td[data-c="'+c+'"]');if(td&&td.innerText!=cells[c])res.cells.push({c:c,v:td.innerText.replace(/ /g,' ').trim()})}
 var bx=document.querySelectorAll('[data-new]');for(i=0;i<bx.length;i++){var b=bx[i],ps2=[],pp=b.querySelectorAll('p');for(j=0;j<pp.length;j++)ps2.push(ser(pp[j],''));
  if(!pp.length&&b.innerText.trim())ps2.push({rs:[{t:b.innerText.trim()}]});if(!ps2.length)continue;
  res.boxes.push({slide:+b.getAttribute('data-slide'),x:b.offsetLeft*.75,y:b.offsetTop*.75,w:b.offsetWidth*.75,h:b.offsetHeight*.75,ps:ps2})}
 res.tables=[];var nt=document.querySelectorAll('[data-newtbl]');for(i=0;i<nt.length;i++){var rows=[],trs=nt[i].querySelectorAll('tr');
  for(j=0;j<trs.length;j++){var cs2=[],tds=trs[j].querySelectorAll('td');for(var k=0;k<tds.length;k++){var cp=[],pp2=tds[k].querySelectorAll('p');
   for(var m=0;m<pp2.length;m++)cp.push(ser(pp2[m],''));if(!cp.length)cp.push({rs:[]});cs2.push(cp)}rows.push(cs2)}
  var after=nt[i].getAttribute('data-after');res.tables.push({after:after,rows:rows})}
 return res}
function changed(){var r=collect();return r.paras.length+r.cells.length+Object.keys(r.moved).length+r.removed.length+r.boxes.length+r.tables.length>0}
function blk(n){var el=n&&(n.nodeType==1?n:n.parentElement);if(!el)return null;var p=el.closest('[data-p],p,h1,h2,h3,h4,h5,h6');return p&&inEdit(p)?p:null}
function blocks(){var s=getSelection(),out=[];if(!s.rangeCount)return out;var r=s.getRangeAt(0);
 var all=document.querySelectorAll('[contenteditable="true"] [data-p],[contenteditable="true"] p');
 for(var i=0;i<all.length;i++)if(r.intersectsNode(all[i])&&out.indexOf(all[i])<0)out.push(all[i]);
 if(!out.length){var b=blk(s.anchorNode);if(b)out.push(b)}return out}
function cur0(el){return el.getAttribute('data-list')||el.getAttribute('data-olist')||'none'}
function renum(){var n=0,ps=document.querySelectorAll('[data-list]');for(var i=0;i<ps.length;i++){var b=ps[i].querySelector(':scope > .bul');
 var k=ps[i].getAttribute('data-list');if(k=='number'){n++;if(b)b.textContent=n+'.'}else n=0}}
function list(kind){var bs=blocks();if(!bs.length)return;var all=bs.every(function(e){return cur0(e)==kind}),nk=all?'none':kind;
 for(var i=0;i<bs.length;i++){var e=bs[i];e.setAttribute('data-list',nk);var b=e.querySelector(':scope > .bul');if(b)b.remove();
  if(nk!='none'){b=document.createElement('span');b.className='bul';b.contentEditable='false';b.style.cssText='display:inline-block;min-width:14pt;text-indent:0';
   b.textContent=nk=='bullet'?'•':'1.';e.insertBefore(b,e.firstChild);e.style.marginLeft='18pt';e.style.textIndent='-14pt'}else{e.style.textIndent='';e.style.marginLeft=''}}
 renum();fitDoc()}
function indent(d){var bs=blocks();for(var i=0;i<bs.length;i++){var e=bs[i];if(!e.hasAttribute('data-ml0'))e.setAttribute('data-ml0',parseFloat(getComputedStyle(e).marginLeft)||0);
 var st=Math.max(-20,Math.min(20,(+(e.getAttribute('data-indd')||0))+d));var ml=Math.max(0,+e.getAttribute('data-ml0')+st*48);
 e.setAttribute('data-indd',st);e.style.marginLeft=ml+'px'}fitDoc()}
function spacing(x){var bs=blocks();for(var i=0;i<bs.length;i++){bs[i].setAttribute('data-ls',x);bs[i].style.lineHeight=String(x*1.15)}fitDoc()}
function after(pt){var bs=blocks();for(var i=0;i<bs.length;i++){bs[i].setAttribute('data-sa',pt);bs[i].style.marginBottom=pt+'pt'}fitDoc()}
function topBlock(n){var el=n&&(n.nodeType==1?n:n.parentElement);while(el&&el.parentElement&&!el.parentElement.classList.contains('page'))el=el.parentElement;
 return el&&el.parentElement?el:null}
var nt=0;
function insTable(r,c){var s=getSelection();if(!s.rangeCount)return false;var b=topBlock(s.anchorNode);if(!b||!inEdit(b))return false;snapshot();
 var anc=b.hasAttribute('data-p')?b:null;if(!anc){var q=b.querySelectorAll('[data-p]');anc=q.length?q[q.length-1]:null}
 if(!anc){var prev=b;while(prev&&!anc){prev=prev.previousElementSibling;if(prev)anc=prev.hasAttribute('data-p')?prev:prev.querySelector('[data-p]')}}
 nt++;var d=document.createElement('div');d.setAttribute('data-newtbl',nt);d.setAttribute('data-after',anc?anc.getAttribute('data-p'):'');
 var h='<table class="t" style="width:100%">';for(var i=0;i<r;i++){h+='<tr>';for(var j=0;j<c;j++)h+='<td><p><br></p></td>';h+='</tr>'}
 d.innerHTML=h+'</table>';b.parentNode.insertBefore(d,b.nextSibling);var p0=d.querySelector('p');var rg=document.createRange();rg.setStart(p0,0);rg.collapse(true);
 s.removeAllRanges();s.addRange(rg);fitDoc();return true}
function focusSel(){var s=getSelection();if(!s.rangeCount)return;var r=s.getRangeAt(0).cloneRange(),n=r.startContainer,el=n.nodeType==1?n:n.parentElement;
 var root=el&&el.closest('[contenteditable="true"]');if(root&&document.activeElement!==root){root.focus({preventScroll:true});s.removeAllRanges();s.addRange(r)}}
function insImg(k,src,w,h){var s=getSelection();if(!s.rangeCount||!blk(s.anchorNode))return false;snapshot();focusSel();
 insNode('<span data-newimg="'+k+'" data-w="'+w+'" data-h="'+h+'" contenteditable="false"><img src="'+src+'" style="width:'+w+'pt;max-width:100%;height:auto;vertical-align:middle"></span>');
 fitDoc();return true}
function insPB(){var s=getSelection();if(!s.rangeCount||!blk(s.anchorNode))return false;snapshot();focusSel();
 insNode('<span class="pb" data-newpb="1" contenteditable="false"></span>');return true}
function insNode(html){var s=getSelection(),r=s.getRangeAt(0);r.deleteContents();var t=document.createElement('div');t.innerHTML=html;var n=t.firstChild;
 var c=r.startContainer;if(c.nodeType==1&&c.classList&&c.classList.contains('page')){var b=blk(c.childNodes[r.startOffset]||c.lastChild);if(b){r=document.createRange();r.selectNodeContents(b);r.collapse(false)}}
 r.insertNode(n);r=document.createRange();r.setStartAfter(n);r.collapse(true);s.removeAllRanges();s.addRange(r);return n}
function lit(t,f,r,mc){var a=mc?t:t.toLowerCase(),b=mc?f:f.toLowerCase(),o='',p=0,n=0,k;
 while((k=a.indexOf(b,p))>=0){o+=t.substring(p,k)+r;p=k+f.length;n++}return[o+t.substring(p),n]}
function replaceAll(f,r,mc){if(!f)return 0;snapshot();var n=0,rs=roots();
 for(var i=0;i<rs.length;i++){if(rs[i].contentEditable!='true')continue;var w=document.createTreeWalker(rs[i],NodeFilter.SHOW_TEXT),t,list2=[];
  while(t=w.nextNode())list2.push(t);for(var j=0;j<list2.length;j++){var x=list2[j];if(x.parentElement.closest('[contenteditable="false"]'))continue;
   var res=lit(x.data,f,r,mc);if(res[1]){n+=res[1];x.data=res[0]}}}fitDoc();return n}
function stats(){var t='',rs=document.querySelectorAll(K=='docx'?'.page':K=='pptx'?'.slide':'td');for(var i=0;i<rs.length;i++){
  var c=rs[i].cloneNode(true),x=c.querySelectorAll('.pgno,.bul,.no');for(var j=0;j<x.length;j++)x[j].remove();t+=' '+c.textContent}
 var w=t.trim().split(/\s+/).filter(function(s){return s.length}).length;
 return JSON.stringify({words:w,chars:t.replace(/\s+/g,' ').trim().length,nospace:t.replace(/\s/g,'').length,paras:document.querySelectorAll('[data-p]').length})}
function font(name){document.execCommand('fontName',false,name);fitDoc()}
function sizeTo(pt){var s=getSelection();if(!s.rangeCount||s.isCollapsed)return;
 document.execCommand('styleWithCSS',false,false);document.execCommand('fontSize',false,'7');document.execCommand('styleWithCSS',false,true);
 var f=document.querySelectorAll('font[size="7"]');for(var i=0;i<f.length;i++){var sp=document.createElement('span');sp.style.fontSize=pt+'pt';
  while(f[i].firstChild)sp.appendChild(f[i].firstChild);f[i].parentNode.replaceChild(sp,f[i]);
  var inn=sp.querySelectorAll('[style]');for(var j=0;j<inn.length;j++)inn[j].style.fontSize=''}fitDoc()}
function cmd(c,v){focusSel();document.execCommand(c,false,v==null?null:v);fitDoc()}
function size(d){var s=getSelection();if(!s.rangeCount||s.isCollapsed)return;var n=s.anchorNode,el=n&&(n.nodeType==1?n:n.parentElement);
 var cur=el?parseFloat(getComputedStyle(el).fontSize)*.75:11,nv=Math.max(4,Math.round(cur+d));
 document.execCommand('styleWithCSS',false,false);document.execCommand('fontSize',false,'7');document.execCommand('styleWithCSS',false,true);
 var f=document.querySelectorAll('font[size="7"]');for(var i=0;i<f.length;i++){var sp=document.createElement('span');sp.style.fontSize=nv+'pt';
  while(f[i].firstChild)sp.appendChild(f[i].firstChild);f[i].parentNode.replaceChild(sp,f[i]);
  var inn=sp.querySelectorAll('[style]');for(var j=0;j<inn.length;j++)inn[j].style.fontSize=''}fitDoc()}
function pick(el){if(sel){sel.classList.remove('qsel');var h=sel.querySelector(':scope > .hdl');if(h)h.remove()}sel=el;
 if(el){el.classList.add('qsel');var hd=document.createElement('div');hd.className='hdl';hd.contentEditable='false';el.appendChild(hd)}}
function kOf(s){var m=(s&&s.style.transform||'').match(/scale\(([\d.]+)/);return m?parseFloat(m[1]):1}
function down(x,y,target){if(!lay)return false;var el=target.closest('.sp[data-s],.sp[data-new]');if(!el){pick(null);return false}
 if(el!==sel)pick(el);drag={el:el,x:x,y:y,l:el.offsetLeft,t:el.offsetTop,w:el.offsetWidth,h:el.offsetHeight,k:kOf(el.closest('.slide')),rs:target.classList.contains('hdl')};return true}
function move(x,y){if(!drag)return;var dx=(x-drag.x)/drag.k,dy=(y-drag.y)/drag.k,el=drag.el;
 if(drag.rs){el.style.width=Math.max(16,drag.w+dx)+'px';el.style.height=Math.max(10,drag.h+dy)+'px'}else{el.style.left=(drag.l+dx)+'px';el.style.top=(drag.t+dy)+'px'}}
function up(){if(!drag)return;var el=drag.el;drag=null;var s=el.getAttribute('data-s');
 if(s)moved[s]={x:el.offsetLeft*.75,y:el.offsetTop*.75,w:el.offsetWidth*.75,h:el.offsetHeight*.75}}
document.addEventListener('touchstart',function(e){var t=e.touches[0];if(down(t.clientX,t.clientY,e.target))e.preventDefault()},{passive:false});
document.addEventListener('touchmove',function(e){if(!drag)return;var t=e.touches[0];move(t.clientX,t.clientY);e.preventDefault()},{passive:false});
document.addEventListener('touchend',up);
document.addEventListener('mousedown',function(e){if(down(e.clientX,e.clientY,e.target))e.preventDefault()});
document.addEventListener('mousemove',function(e){move(e.clientX,e.clientY)});
document.addEventListener('mouseup',up);
function del(){if(!sel)return;var el=sel;pick(null);var s=el.getAttribute('data-s');if(s)removed[s]=1;el.remove()}
function cur(){var w=document.querySelectorAll('.slide-wrap'),b=0;for(var i=0;i<w.length;i++){if(w[i].getBoundingClientRect().top<innerHeight*0.45)b=i}return b}
function addBox(txt){var w=document.querySelectorAll('.slide-wrap')[cur()];if(!w)return;var s=w.querySelector('.slide');snapshot();nb++;
 var d=document.createElement('div');d.className='sp';d.setAttribute('data-new',nb);d.setAttribute('data-slide',w.id.substring(1));
 d.style.cssText='left:'+(s.offsetWidth*.15)+'px;top:'+(s.offsetHeight*.4)+'px;width:'+(s.offsetWidth*.7)+'px;height:auto;min-height:30px;padding:4pt 7pt;font-size:24pt;color:#000;justify-content:flex-start';
 d.innerHTML='<p>'+txt+'</p>';s.appendChild(d);
 if(lay){pick(d)}else{d.contentEditable='true';d.focus();var r=document.createRange();r.selectNodeContents(d.firstChild);var se=getSelection();se.removeAllRanges();se.addRange(r)}}
function mkPage(){var p=document.createElement('div');p.className='page';if(editing)p.contentEditable='true';return p}
function paginate(){var root=document.getElementById('doc');if(!root||!PG)return;var blocks=[],pages=root.querySelectorAll('.page'),i,j;
 for(i=0;i<pages.length;i++){var c=pages[i].children;for(j=0;j<c.length;j++)if(!c[j].classList.contains('pgno'))blocks.push(c[j])}
 root.innerHTML='';var mob=view=='mobile',pg=mkPage();root.appendChild(pg);var lim=PG.h*4/3+2;
 for(i=0;i<blocks.length;i++){var b=blocks[i],br=b.getAttribute('data-brk');
  if(!mob&&br=='before'&&pg.children.length){pg=mkPage();root.appendChild(pg)}
  pg.appendChild(b);
  if(!mob&&pg.scrollHeight>lim&&pg.children.length>1){pg.removeChild(b);pg=mkPage();root.appendChild(pg);pg.appendChild(b)}
  if(!mob&&br=='after'&&i<blocks.length-1){pg=mkPage();root.appendChild(pg)}}
 if(!mob){var all=root.querySelectorAll('.page');for(i=0;i<all.length;i++){var n=document.createElement('div');n.className='pgno';n.contentEditable='false';
  n.textContent=(i+1)+' / '+all.length;all[i].appendChild(n)}}
 fitDoc()}
function fitDoc(){if(K!='docx')return;var root=document.getElementById('doc'),wrap=document.getElementById('docwrap');if(!root||!PG)return;
 if(view=='mobile'){root.style.transform='';root.style.width='';root.style.marginLeft='';wrap.style.height='';return}
 var cw=document.documentElement.clientWidth,W=PG.w*4/3+24,k=Math.min(1,cw/W);root.style.width=W+'px';root.style.transformOrigin='0 0';
 root.style.transform='scale('+k+')';root.style.marginLeft=Math.max(0,(cw-W*k)/2)+'px';wrap.style.height=Math.ceil(root.offsetHeight*k)+'px'}
function outline(on){var o=document.getElementById('outline');if(!o)return;if(on&&!o.hasChildNodes()){var w=document.querySelectorAll('.slide-wrap'),h='';
 for(var i=0;i<w.length;i++){var ps=w[i].querySelectorAll('.sp p'),t='';for(var j=0;j<ps.length;j++){var x=ps[j].innerText.trim();if(x)t+='<p>'+x.replace(/</g,'&lt;')+'</p>'}
  h+='<div class="oc"><div class="on">'+(i+1)+'</div>'+(t||'<p style="color:#999">—</p>')+'</div>'}o.innerHTML=h}}
function cards(on){var sh=document.querySelectorAll('.sheet');for(var s=0;s<sh.length;s++){var t=sh[s].querySelector('table.x'),cd=sh[s].querySelector('.cards');
 if(!t)continue;if(on&&!cd){cd=document.createElement('div');cd.className='cards';var rows=t.rows,head=rows.length>1?rows[1]:null,h='';
  for(var r=2;r<rows.length;r++){var cs=rows[r].cells,line='';for(var c=1;c<cs.length;c++){var v=cs[c].innerText.trim();if(!v)continue;
   var lab=head&&head.cells[c]?head.cells[c].innerText.trim():'';if(!lab)lab=rows[0].cells[c]?rows[0].cells[c].innerText:'';
   line+='<div class="kv"><span>'+lab.replace(/</g,'&lt;')+'</span><b>'+v.replace(/</g,'&lt;')+'</b></div>'}
   if(line)h+='<div class="card"><div class="rn">'+rows[r].cells[0].innerText+'</div>'+line+'</div>'}
  cd.innerHTML=h||'<p style="padding:16px">—</p>';sh[s].appendChild(cd)}}}
function setView(v){view=v;document.body.classList.toggle('mobile',v=='mobile');if(K=='docx')paginate();else if(K=='pptx'){outline(v=='mobile');if(v!='mobile'&&window.fit)fit()}
 else if(K=='xlsx')cards(v=='mobile')}
function init(){if(K=='docx'){try{PG=JSON.parse(document.body.getAttribute('data-pg'))}catch(e){}paginate()}}
if(document.readyState=='complete')init();else window.addEventListener('load',init);
window.addEventListener('resize',function(){fitDoc()});
document.addEventListener('input',function(){if(K=='docx'&&view!='mobile')fitDoc()});
return{edit:edit,layout:layout,collect:function(){return JSON.stringify(collect())},changed:changed,cmd:cmd,size:size,del:del,addBox:addBox,cur:cur,view:setView,
 list:list,indent:indent,spacing:spacing,after:after,insTable:insTable,insImg:insImg,insPB:insPB,replaceAll:replaceAll,stats:stats,font:font,sizeTo:sizeTo}
})();
"""
